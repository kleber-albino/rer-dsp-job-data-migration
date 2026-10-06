#!/usr/bin/env bash
# Usage: run via ./setup.sh (publishes layers from mapLayersConfig.json for WFS downloads)

set -euo pipefail

GEOSERVER_URL="${GEOSERVER_URL:-http://localhost:8080/geoserver}"
GEOSERVER_USER="${GEOSERVER_ADMIN_USER:-admin}"
GEOSERVER_PASSWORD="${GEOSERVER_ADMIN_PASSWORD:-geoserver}"

WORKSPACE_NAME="${WORKSPACE_NAME:-dsp}"
DATASTORE_NAME="${DATASTORE_NAME:-dsp-db}"

DB_HOST="${DB_HOST:-dsp-geoserver-db}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-dsp-geoserver-db}"
DB_SCHEMA="${DB_SCHEMA:-dsp}"
DB_USER="${DB_USER:-dsp_geo}"
DB_PASSWORD="${DB_PASSWORD:-dsp_geo}"

MAP_LAYERS_CONFIG="${MAP_LAYERS_CONFIG:-/config/mapLayersConfig.json}"

# Legacy env fallbacks for the four fixed layers (when JSON has no srs field).
declare -A FIXED_LAYER_SRS_ENV=(
  ["dsp:territory-level-1"]="LAYER_SRS_TERRITORY_LEVEL_1"
  ["dsp:territory-level-2"]="LAYER_SRS_TERRITORY_LEVEL_2"
  ["dsp:territory-level-3"]="LAYER_SRS_TERRITORY_LEVEL_3"
  ["dsp:area-of-interest"]="LAYER_SRS_AREA_OF_INTEREST"
)

declare -A FIXED_NATIVE_NAMES=(
  ["dsp:territory-level-1"]="territory_level_1"
  ["dsp:territory-level-2"]="territory_level_2"
  ["dsp:territory-level-3"]="territory_level_3"
  ["dsp:area-of-interest"]="area_of_interest"
)

declare -A FIXED_STROKE_WIDTH=(
  ["dsp:territory-level-1"]="1.5"
  ["dsp:territory-level-2"]="1.5"
  ["dsp:territory-level-3"]="1.5"
  ["dsp:area-of-interest"]="1"
)

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to read mapLayersConfig.json."
  exit 1
fi

if [ ! -f "$MAP_LAYERS_CONFIG" ]; then
  echo "mapLayersConfig.json not found: $MAP_LAYERS_CONFIG"
  echo "Rebuild the GeoServer image after ./config.sh (./setup.sh or ./start.sh)."
  exit 1
fi

normalize_epsg() {
  local raw=$1
  if [ -z "$raw" ]; then
    echo ""
    return 0
  fi
  if [[ "$raw" =~ ^[Ee][Pp][Ss][Gg]:([0-9]+)$ ]]; then
    echo "EPSG:${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "$raw" =~ ^[0-9]+$ ]]; then
    echo "EPSG:${raw}"
    return 0
  fi
  echo "$raw"
}

layer_field() {
  local wms_id=$1
  local field=$2
  jq -r --arg id "$wms_id" --arg field "$field" '
    .groups[]?.layers[]?
    | select(.layers == $id)
    | .[$field] // empty
  ' "$MAP_LAYERS_CONFIG" | head -n 1
}

layer_style_values() {
  local wms_id=$1
  jq -r --arg id "$wms_id" '
    .groups[]?.layers[]?
    | select(.layers == $id)
    | [.style.color // "", .style.fillColor // ""]
    | @tsv
  ' "$MAP_LAYERS_CONFIG" | head -n 1
}

list_wms_ids() {
  jq -r '
    .groups[]?.layers[]?
    | .layers // empty
  ' "$MAP_LAYERS_CONFIG" | awk 'NF' | awk '!seen[$0]++'
}

resolve_native_name() {
  local wms_id=$1
  local from_json
  from_json=$(layer_field "$wms_id" "nativeName")
  if [ -n "$from_json" ]; then
    echo "$from_json"
    return 0
  fi
  if [ -n "${FIXED_NATIVE_NAMES[$wms_id]:-}" ]; then
    echo "${FIXED_NATIVE_NAMES[$wms_id]}"
    return 0
  fi
  # Generic fallback: dsp:foo-bar → foo_bar
  local bare=${wms_id#*:}
  echo "${bare//-/_}"
}

resolve_srs() {
  local wms_id=$1
  local from_json env_name env_value
  from_json=$(normalize_epsg "$(layer_field "$wms_id" "srs")")
  if [ -n "$from_json" ]; then
    echo "$from_json"
    return 0
  fi
  env_name="${FIXED_LAYER_SRS_ENV[$wms_id]:-}"
  if [ -n "$env_name" ]; then
    env_value=$(normalize_epsg "${!env_name:-}")
    if [ -n "$env_value" ]; then
      echo "$env_value"
      return 0
    fi
    echo "Environment variable '${env_name}' is required for layer '${wms_id}' (or set layers[].srs in mapLayersConfig.json)." >&2
    exit 1
  fi
  echo "Layer '${wms_id}' is missing srs in mapLayersConfig.json." >&2
  exit 1
}

resolve_stroke_width() {
  local wms_id=$1
  echo "${FIXED_STROKE_WIDTH[$wms_id]:-1.5}"
}

detect_geometry_kind() {
  local table_name=$1
  local type_raw kind geom_col

  if ! command -v psql >/dev/null 2>&1; then
    echo "polygon"
    return 0
  fi

  type_raw=$(PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Atqc "
    SELECT type FROM geometry_columns
     WHERE f_table_schema = '${DB_SCHEMA}'
       AND f_table_name = '${table_name}'
     LIMIT 1;
  " 2>/dev/null || true)

  if [ -z "$type_raw" ]; then
    geom_col=$(PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Atqc "
      SELECT f_geometry_column FROM geometry_columns
       WHERE f_table_schema = '${DB_SCHEMA}'
         AND f_table_name = '${table_name}'
       LIMIT 1;
    " 2>/dev/null || true)
    if [ -n "$geom_col" ]; then
      type_raw=$(PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Atqc "
        SELECT UPPER(GeometryType(\"${geom_col}\")) FROM \"${DB_SCHEMA}\".\"${table_name}\"
         WHERE \"${geom_col}\" IS NOT NULL LIMIT 1;
      " 2>/dev/null || true)
    fi
  fi

  type_raw=$(echo "$type_raw" | tr '[:lower:]' '[:upper:]')
  case "$type_raw" in
    *POINT*) kind="point" ;;
    *LINE*) kind="line" ;;
    *POLYGON*|*GEOMETRY*|"") kind="polygon" ;;
    *) kind="polygon" ;;
  esac
  echo "$kind"
}

# Tables created by the job (AOI and extra layers) may not yet exist when the first load is scheduled.
table_exists() {
  local table_name=$1
  local found

  if ! command -v psql >/dev/null 2>&1; then
    return 0
  fi

  found=$(PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Atqc "
    SELECT 1
      FROM information_schema.tables
     WHERE table_schema = '${DB_SCHEMA}'
       AND table_name = '${table_name}'
     LIMIT 1;
  " 2>/dev/null || true)

  [ "$found" = "1" ]
}

build_point_sld() {
  local style_name=$1
  local stroke_color=$2
  local fill_color=$3
  local mark_fill="$fill_color"
  if [ "$fill_color" = "transparent" ]; then
    mark_fill="$stroke_color"
  fi
  cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
 xmlns="http://www.opengis.net/sld"
 xmlns:ogc="http://www.opengis.net/ogc"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>${style_name}</Name>
    <UserStyle>
      <Title>${style_name}</Title>
      <FeatureTypeStyle>
        <Rule>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">${mark_fill}</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">${stroke_color}</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>8</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
EOF
}

build_line_sld() {
  local style_name=$1
  local stroke_color=$2
  local stroke_width=$3
  cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
 xmlns="http://www.opengis.net/sld"
 xmlns:ogc="http://www.opengis.net/ogc"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>${style_name}</Name>
    <UserStyle>
      <Title>${style_name}</Title>
      <FeatureTypeStyle>
        <Rule>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">${stroke_color}</CssParameter>
              <CssParameter name="stroke-width">${stroke_width}</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
EOF
}

build_polygon_sld() {
  local style_name=$1
  local stroke_color=$2
  local fill_color=$3
  local stroke_width=$4
  local fill_opacity
  local fill_xml

  if [ "$fill_color" = "transparent" ]; then
    fill_opacity="0.0"
    fill_xml="<CssParameter name=\"fill-opacity\">${fill_opacity}</CssParameter>"
  else
    fill_opacity="0.25"
    fill_xml="<CssParameter name=\"fill\">${fill_color}</CssParameter>
              <CssParameter name=\"fill-opacity\">${fill_opacity}</CssParameter>"
  fi

  cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
 xmlns="http://www.opengis.net/sld"
 xmlns:ogc="http://www.opengis.net/ogc"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>${style_name}</Name>
    <UserStyle>
      <Title>${style_name}</Title>
      <FeatureTypeStyle>
        <Rule>
          <PolygonSymbolizer>
            <Fill>
              ${fill_xml}
            </Fill>
            <Stroke>
              <CssParameter name="stroke">${stroke_color}</CssParameter>
              <CssParameter name="stroke-width">${stroke_width}</CssParameter>
            </Stroke>
          </PolygonSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
EOF
}

build_sld_for_kind() {
  local kind=$1
  local style_name=$2
  local stroke_color=$3
  local fill_color=$4
  local stroke_width=$5
  case "$kind" in
    point) build_point_sld "$style_name" "$stroke_color" "$fill_color" ;;
    line) build_line_sld "$style_name" "$stroke_color" "$stroke_width" ;;
    *) build_polygon_sld "$style_name" "$stroke_color" "$fill_color" "$stroke_width" ;;
  esac
}

rest_request() {
  local method=$1
  local url=$2
  local data=${3:-}

  if [ "$method" = "GET" ]; then
    curl -s -w "\n%{http_code}" -u "${GEOSERVER_USER}:${GEOSERVER_PASSWORD}" \
      -X GET -H "Accept: application/json" \
      "${GEOSERVER_URL}${url}"
  else
    curl -s -w "\n%{http_code}" -u "${GEOSERVER_USER}:${GEOSERVER_PASSWORD}" \
      -X "$method" -H "Content-Type: application/json" \
      -d "$data" \
      "${GEOSERVER_URL}${url}"
  fi
}

rest_status() {
  local method=$1
  local url=$2
  local data=${3:-}

  if [ "$method" = "GET" ]; then
    curl -s -o /dev/null -w "%{http_code}" -u "${GEOSERVER_USER}:${GEOSERVER_PASSWORD}" \
      -X GET -H "Accept: application/json" \
      "${GEOSERVER_URL}${url}"
  else
    curl -s -o /dev/null -w "%{http_code}" -u "${GEOSERVER_USER}:${GEOSERVER_PASSWORD}" \
      -X "$method" -H "Content-Type: application/json" \
      -d "$data" \
      "${GEOSERVER_URL}${url}"
  fi
}

ensure_workspace() {
  local status
  status=$(rest_status "GET" "/rest/workspaces/${WORKSPACE_NAME}")
  if [ "$status" = "200" ]; then
    echo "Workspace '${WORKSPACE_NAME}' already exists"
    return 0
  fi

  echo "Creating workspace '${WORKSPACE_NAME}'"
  local response status_code
  response=$(rest_request "POST" "/rest/workspaces" "{\"workspace\":{\"name\":\"${WORKSPACE_NAME}\"}}")
  status_code=$(echo "$response" | tail -n 1)
  if [ "$status_code" != "201" ]; then
    echo "Failed to create workspace (HTTP ${status_code})"
    echo "$response" | head -n -1
    exit 1
  fi
  echo "Workspace created"
}

ensure_datastore() {
  local status
  status=$(rest_status "GET" "/rest/workspaces/${WORKSPACE_NAME}/datastores/${DATASTORE_NAME}")
  if [ "$status" = "200" ]; then
    echo "Datastore '${DATASTORE_NAME}' already exists"
    return 0
  fi

  echo "Creating PostGIS datastore '${DATASTORE_NAME}' -> ${DB_HOST}:${DB_PORT}/${DB_NAME} schema=${DB_SCHEMA}"
  local data
  data=$(cat <<EOF
{
  "dataStore": {
    "name": "${DATASTORE_NAME}",
    "type": "PostGIS",
    "enabled": true,
    "connectionParameters": {
      "entry": [
        {"@key": "host", "\$": "${DB_HOST}"},
        {"@key": "port", "\$": "${DB_PORT}"},
        {"@key": "database", "\$": "${DB_NAME}"},
        {"@key": "schema", "\$": "${DB_SCHEMA}"},
        {"@key": "user", "\$": "${DB_USER}"},
        {"@key": "passwd", "\$": "${DB_PASSWORD}"},
        {"@key": "dbtype", "\$": "postgis"},
        {"@key": "Expose primary keys", "\$": "true"}
      ]
    }
  }
}
EOF
)

  local response status_code
  response=$(rest_request "POST" "/rest/workspaces/${WORKSPACE_NAME}/datastores" "$data")
  status_code=$(echo "$response" | tail -n 1)
  if [ "$status_code" != "201" ]; then
    echo "Failed to create datastore (HTTP ${status_code})"
    echo "$response" | head -n -1
    exit 1
  fi
  echo "Datastore created"
}

# CSV download dates: UTC ISO-8601 with a literal Z (yyyy-MM-dd'T'HH:mm:ss'Z').
# The pattern quotes T and Z so SimpleDateFormat does not treat Z as an RFC 822 offset.
ensure_csv_date_format() {
  local pattern="yyyy-MM-dd'T'HH:mm:ss'Z'"
  echo "Setting WFS CSV date format to ${pattern} (UTC)"

  local response status_code body payload put_response put_status
  response=$(rest_request "GET" "/rest/services/wfs/settings.json")
  status_code=$(echo "$response" | tail -n 1)
  body=$(echo "$response" | sed '$d')
  if [ "$status_code" != "200" ]; then
    echo "Failed to read WFS settings (HTTP ${status_code})"
    echo "$body"
    exit 1
  fi

  payload=$(echo "$body" | jq --arg fmt "$pattern" '
    if .wfs != null then
      .wfs.csvDateFormat = $fmt
    else
      .csvDateFormat = $fmt
    end
  ')
  if [ -z "$payload" ] || [ "$payload" = "null" ]; then
    echo "WFS settings JSON could not be updated with csvDateFormat"
    echo "$body"
    exit 1
  fi

  put_response=$(rest_request "PUT" "/rest/services/wfs/settings" "$payload")
  put_status=$(echo "$put_response" | tail -n 1)
  if [ "$put_status" != "200" ]; then
    echo "Failed to set WFS CSV date format (HTTP ${put_status})"
    echo "$put_response" | sed '$d'
    exit 1
  fi

  local stored
  stored=$(rest_request "GET" "/rest/services/wfs/settings.json" | sed '$d' | jq -r '
    if .wfs != null then .wfs.csvDateFormat else .csvDateFormat end
  ')
  if [ "$stored" != "$pattern" ]; then
    echo "WFS csvDateFormat is '${stored}', expected '${pattern}'"
    exit 1
  fi
  echo "WFS CSV date format set"
}

upload_sld() {
  local style_name=$1
  local sld_content=$2
  local put_response put_status

  put_response=$(curl -s -w "\n%{http_code}" -u "${GEOSERVER_USER}:${GEOSERVER_PASSWORD}" \
    -X PUT -H "Content-Type: application/vnd.ogc.sld+xml" \
    -d "$sld_content" \
    "${GEOSERVER_URL}/rest/workspaces/${WORKSPACE_NAME}/styles/${style_name}")
  put_status=$(echo "$put_response" | tail -n 1)
  if [ "$put_status" != "200" ]; then
    echo "Failed to upload SLD for '${style_name}' (HTTP ${put_status})"
    echo "$put_response" | head -n -1
    exit 1
  fi
}

ensure_style() {
  local style_name=$1
  local sld_content=$2

  local status
  status=$(rest_status "GET" "/rest/workspaces/${WORKSPACE_NAME}/styles/${style_name}")
  if [ "$status" != "200" ]; then
    echo "Creating style '${style_name}'"
    local response status_code
    response=$(rest_request "POST" "/rest/workspaces/${WORKSPACE_NAME}/styles" \
      "{\"style\":{\"name\":\"${style_name}\",\"filename\":\"${style_name}.sld\"}}")
    status_code=$(echo "$response" | tail -n 1)
    if [ "$status_code" != "201" ]; then
      echo "Failed to create style '${style_name}' (HTTP ${status_code})"
      echo "$response" | head -n -1
      exit 1
    fi
  else
    echo "Updating style '${style_name}' from mapLayersConfig"
  fi

  upload_sld "$style_name" "$sld_content"
  echo "Style '${style_name}' ready"
}

apply_default_style() {
  local layer_name=$1
  local style_name=$2
  local style_payload

  style_payload=$(cat <<EOF
{
  "layer": {
    "defaultStyle": {
      "name": "${style_name}",
      "workspace": "${WORKSPACE_NAME}"
    }
  }
}
EOF
)
  rest_request "PUT" "/rest/layers/${WORKSPACE_NAME}:${layer_name}" "$style_payload" >/dev/null
}

publish_layer() {
  local layer_name=$1
  local table_name=$2
  local style_name=$3
  local srs=$4

  if [ -z "$srs" ]; then
    echo "SRS is required to publish layer '${layer_name}'"
    exit 1
  fi

  local status
  status=$(rest_status "GET" "/rest/layers/${WORKSPACE_NAME}:${layer_name}")
  if [ "$status" = "200" ]; then
    echo "Layer '${layer_name}' already exists — syncing default style"
    apply_default_style "$layer_name" "$style_name"
    return 0
  fi

  echo "Publishing layer '${layer_name}' (table ${table_name})"
  local data
  data=$(cat <<EOF
{
  "featureType": {
    "name": "${layer_name}",
    "nativeName": "${table_name}",
    "title": "${layer_name}",
    "enabled": true,
    "srs": "${srs}",
    "projectionPolicy": "FORCE_DECLARED",
    "nativeBoundingBox": {
      "minx": -74.0,
      "maxx": -34.0,
      "miny": -34.0,
      "maxy": 6.0,
      "crs": "${srs}"
    },
    "latLonBoundingBox": {
      "minx": -74.0,
      "maxx": -34.0,
      "miny": -34.0,
      "maxy": 6.0,
      "crs": "EPSG:4326"
    }
  }
}
EOF
)

  local response status_code
  response=$(rest_request "POST" \
    "/rest/workspaces/${WORKSPACE_NAME}/datastores/${DATASTORE_NAME}/featuretypes" "$data")
  status_code=$(echo "$response" | tail -n 1)
  if [ "$status_code" != "201" ]; then
    echo "Failed to publish layer '${layer_name}' (HTTP ${status_code})"
    echo "$response" | head -n -1
    echo "Check: table ${DB_SCHEMA}.${table_name} exists and has geometry (migration must run before populate)."
    return 1
  fi

  apply_default_style "$layer_name" "$style_name"
  echo "Layer '${layer_name}' published"
}

sync_layer_from_config() {
  local wms_id=$1
  local layer_name=${wms_id#*:}
  local table_name style_name stroke_width srs
  local style_tsv stroke_color fill_color geom_kind sld

  table_name=$(resolve_native_name "$wms_id")
  if ! table_exists "$table_name"; then
    echo "Skipping '${wms_id}': table ${DB_SCHEMA}.${table_name} does not exist yet (run migration, then populate again)."
    SKIPPED_LAYERS=$((SKIPPED_LAYERS + 1))
    return 0
  fi

  style_name="dsp_${layer_name//-/_}"
  stroke_width=$(resolve_stroke_width "$wms_id")
  srs=$(resolve_srs "$wms_id")

  style_tsv=$(layer_style_values "$wms_id")
  if [ -z "$style_tsv" ]; then
    echo "Layer '${wms_id}' not found in ${MAP_LAYERS_CONFIG}"
    exit 1
  fi

  stroke_color=$(echo "$style_tsv" | cut -f1)
  fill_color=$(echo "$style_tsv" | cut -f2)
  if [ -z "$stroke_color" ] || [ -z "$fill_color" ]; then
    echo "Layer '${wms_id}' is missing style.color or style.fillColor in mapLayersConfig.json"
    exit 1
  fi

  geom_kind=$(detect_geometry_kind "$table_name")
  echo "  ${wms_id}: table=${table_name} srs=${srs} geom=${geom_kind} color=${stroke_color} fillColor=${fill_color}"
  sld=$(build_sld_for_kind "$geom_kind" "$style_name" "$stroke_color" "$fill_color" "$stroke_width")
  ensure_style "$style_name" "$sld"
  publish_layer "$layer_name" "$table_name" "$style_name" "$srs"
}

SKIPPED_LAYERS=0

echo "=== DSP GeoServer Download populate ==="
echo "URL: ${GEOSERVER_URL}"
echo "DB:  ${DB_HOST}:${DB_PORT}/${DB_NAME} schema=${DB_SCHEMA}"
echo "Map layers config: ${MAP_LAYERS_CONFIG}"
echo ""

ensure_workspace
ensure_datastore
ensure_csv_date_format

echo ""
echo "=== Syncing styles and layers from mapLayersConfig.json ==="
mapfile -t WMS_IDS < <(list_wms_ids)
if [ "${#WMS_IDS[@]}" -eq 0 ]; then
  echo "No layers found in ${MAP_LAYERS_CONFIG}"
  exit 1
fi

for wms_id in "${WMS_IDS[@]}"; do
  sync_layer_from_config "$wms_id"
done

echo ""
echo "Done. WFS: ${GEOSERVER_URL}/${WORKSPACE_NAME}/wfs"
echo "Published $(( ${#WMS_IDS[@]} - SKIPPED_LAYERS )) of ${#WMS_IDS[@]} layer(s) from mapLayersConfig.json"
if [ "$SKIPPED_LAYERS" -gt 0 ]; then
  echo "Skipped ${SKIPPED_LAYERS} layer(s) whose table does not exist yet."
fi
