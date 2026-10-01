#!/bin/sh
# Starts PerseveREST inside RESTgym. RESTgym provides API, HOST, PORT and TIME_BUDGET (minutes) and mounts
# the API's specification at /specifications/$API-openapi.json (and /specifications/$API.yaml).

API_DIR=/apis/restgym-api
mkdir -p "$API_DIR/specifications"

# Use the JSON specification; if it is missing or empty, the YAML one (RestTestGen converts it to JSON)
if [ -s "/specifications/${API}-openapi.json" ]; then
    cp "/specifications/${API}-openapi.json" "$API_DIR/specifications/openapi.json"
else
    cp "/specifications/${API}.yaml" "$API_DIR/specifications/openapi.yaml"
fi
echo "host: http://$HOST:$PORT" > "$API_DIR/api-config.yml"

# RESTgym stops the container when the time budget is over. If the tool exits earlier, even with an
# error, start it again: a stopped container makes RESTgym abort the whole run.
while true; do
    java -XX:MaxRAMPercentage=50 -jar /app/app.jar -a restgym-api -s PerseveRESTStrategy || sleep 1
done
