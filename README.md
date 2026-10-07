# PerseveREST

PerseveREST is an automated black-box testing tool for REST APIs, submitted to
[REST League 2027](https://seunivr.github.io/RestLeague/2027/) (ICSE 2027). It only needs the OpenAPI
specification and the address of the API. It does not use language models (RestTestGen's optional LLM value
provider stays disabled), and at run time it only connects to the API under test.

PerseveREST is built on [RestTestGen](https://github.com/SeUniVr/RestTestGen) 25.12. It keeps RestTestGen's
nominal and mutation-based testing, and adds a strategy that keeps testing for the whole time budget, looks for
server errors with new messages, and avoids crashing or blocking the API under test.

## How it works

- **Testing in rounds for the whole time budget.** RestTestGen performs one pass over the operations and exits.
  PerseveREST repeats rounds in the same process until the container is stopped, so values learned from the
  responses (identifiers, names) are kept. Each round first tests the operations that never returned a successful
  response, in the order of RestTestGen's operation dependency graph, then all operations in random order.
- **Exploring new faults.** After the first successful request of each operation, and after every server error
  whose message is new, PerseveREST sends about 60 mutated variants of the request (RestTestGen's intensification
  mutators: invalid, missing and extra parameters, boundary values, other HTTP methods). Error messages are
  compared by the similarity of their words, ignoring the parts that change at every response (timestamps, paths,
  identifiers, echoed inputs), so the exploration goes to genuinely different faults.
- **Malformed requests.** Once per operation, PerseveREST also sends a few malformed variants of one of its requests
  (without body, with empty, truncated or invalid JSON, with other content types, with a trailing slash), which test
  how the API handles requests that never reach its business logic.
- **Invalid values, field by field.** Once per operation, every property of a request body (up to 20), every
  query parameter and every path parameter gets invalid values for its kind: null, empty and very long strings,
  values of another JSON type, numbers too large for any numeric type, negative and decimal numbers. RestTestGen's
  mutators change one random parameter at a time, so the code that reads most fields was never reached with
  invalid values. Numbers of amounts of resources never get huge values.
- **Valid request bodies for combined schemas.** Schemas combined with `allOf` are merged, and one schema is chosen
  for `oneOf` and `anyOf`, before generating values. RestTestGen 25.12 did not resolve them, so bodies built from
  them were malformed (e.g., `{"name": "Leo", "type": }` for the pets of Spring PetClinic), and it discarded request
  bodies whose root schema is combined, so, e.g., every request creating or updating a vet, a visit, a pet or a pet
  type of Spring PetClinic was sent without a body (it also lost the schemas of combined schemas when copying them).
  Request bodies that are a single value (e.g., the identifier of a patient, as a JSON string) were discarded too;
  PerseveREST sends them.
- **Complete request bodies.** Most specifications do not say which properties of a request body are required (none
  do in 11 of the 14 APIs of RESTgym with JSON bodies), and RestTestGen includes each property that is not required
  with a probability of 10%: its bodies were almost always empty or incomplete, and the API rejected them. PerseveREST
  chooses the probability for each request among 10%, 50% and 90%, so it sends both minimal and complete bodies.
- **Reusing resource identifiers.** The identifiers in successful responses are remembered for their collection
  (e.g., `/hospitals` for `POST /hospitals`), and path parameters named like identifiers (`hospital_id`, `petId`)
  usually get one of those of the preceding collection. RestTestGen links values to parameters by name only, so an
  `id` returned by `POST /hospitals` was never used for `/hospitals/{hospital_id}`. The values of the other fields
  of the responses are remembered by name, and path parameters with the same name usually get one of them:
  RestTestGen prefers the examples of the specification, so, e.g., most of its requests to Kafka REST Proxy used the
  example cluster `cluster-1` instead of the identifier listed by `GET /v3/clusters`, and failed.
- **Realistic values.** Numbers that the specification leaves unbounded are usually generated in a realistic range
  (e.g., 0-100) instead of the whole integer range, and dates use the standard format of OpenAPI (RFC 3339):
  RestTestGen's `2014/05/21 08:30:00` is rejected by many APIs before reaching their logic. When the example of a
  value is a date pattern (e.g., `dd-MM-yyyy`), a date in that pattern is generated instead of sending the pattern.
- **Not blocking the API.** Numbers of parameters describing amounts of resources to allocate (partitions, replicas,
  threads...) are capped at 10: for example, RestTestGen's `{"partitions_count": 1655500739}` made Kafka REST Proxy
  stop responding.
- **Authentication.** For APIs whose specification declares bearer or OAuth2 authentication, the access token
  returned by a successful login is used in the following requests; after a successful registration, PerseveREST
  logs in with the same credentials; a token rejected with 401 (e.g., after a logout) is dropped.
- **Robustness.** No exception stops the testing loop, the tool is restarted if it ever exits, and its memory stays
  bounded over the whole session: RestTestGen's dictionaries of values kept every distinct value, each with its
  whole request or response, so after 10-25 minutes the memory was full and the tool almost stopped (garbage
  collection) or crashed. They now keep the last 100 values per parameter name (20,000 in total), without the
  requests and responses, and the executed interactions are no longer kept for debugging (each keeps its parsed
  response, e.g., tens of thousands of objects for a list of a few thousand resources).
- **Not waiting for slow responses.** Requests time out after 5 seconds without data, or 10 seconds in total
  (RestTestGen: 11 seconds without data, no total limit). Kafka REST Proxy answers requests about nonexistent brokers
  after 60 seconds and streams the responses of its `records` operations for up to a minute: an earlier version of
  PerseveREST spent about 40% of its hour waiting. RESTgym's proxy still records the responses that arrive later.

## Building and running with RESTgym

PerseveREST runs in [RESTgym](https://github.com/SeUniVr/RESTgym), the infrastructure of REST League: RESTgym starts
the API under test and the tool in Docker containers, records every request and response, and measures the results.

### Requirements

- Docker, installed and running, on Linux, macOS, or Windows with WSL2 (Ubuntu). On Windows, keep RESTgym inside the
  Linux file system (e.g., `~/RESTgym`), not on a Windows drive (`/mnt/c/...`): Docker is slow there, and RESTgym's
  result databases are unreliable.
- At least 4 CPU cores and 8 GB of RAM (16 GB recommended), about 50 GB of disk space for the images of the APIs,
  and an Internet connection (the images of the APIs, and the libraries of PerseveREST, are downloaded when the
  images are built).

### Steps

1. Get RESTgym with its APIs (they are Git submodules):

   ```
   git clone --recurse-submodules https://github.com/SeUniVr/RESTgym.git
   cd RESTgym
   ```

2. Add PerseveREST as a tool (the name of the folder is the name of the tool in RESTgym):

   ```
   git clone https://github.com/hilalkhan1/PerseveREST.git tools/perseverest
   ```

3. Choose what to run. RESTgym runs every enabled tool on every enabled API:
   - `restgym-config.yml`: `time_budget_mins` is the length of each run (60 minutes in REST League; e.g., 10 for a
     quick test);
   - `apis/<api>/restgym-api-config.yml`: `enabled: true` for the APIs to test (e.g., `pet-clinic`,
     `notebook-manager`, `kafka-rest-proxy`), `enabled: false` for the others;
   - `tools/<tool>/restgym-tool-config.yml`: `enabled: true` only for `perseverest` (`tools/perseverest` already has
     it; other tools, e.g., `deeprest` and `restler`, are enabled by default).

4. Build the Docker images (answer `2`, "Build images"). PerseveREST is compiled from its source code here:

   ```
   ./restgym.sh build-images
   ```

5. Run the experiment (answer the number of repetitions, e.g., `1`, then press ENTER). Each run lasts the time
   budget plus a few minutes, one tool and API at a time:

   ```
   ./restgym.sh launch-experiment
   ```

6. Check the runs (press ENTER, then answer `no` twice, so that no run is deleted), and compute the results
   (answer `2`, "Analyze only newly completed and verified runs"):

   ```
   ./restgym.sh verify-data
   ./restgym.sh analyze-data
   ```

`./restgym.sh force-stop` stops an experiment (it stops and removes all RESTgym containers).

### Where the results are

Each run has a folder `results/<api>/perseverest/run-<date>-<time>/` in RESTgym:

| File | Content |
|---|---|
| `summary.json` | Results of the run (after `analyze-data`): requests, 2XX/4XX/5XX responses, covered operations, unique 5XX (distinct server errors), branch, line and method coverage of the API |
| `results.db` | Every request and response, with their times (SQLite database, e.g., for DB Browser for SQLite) |
| `logs/perseverest-stdout.log` | Log of PerseveREST: rounds, explored faults, warnings |
| `logs/<api>-stdout.log` | Log of the API under test (e.g., the stack traces of its server errors) |
| `code-coverage/` | Code coverage of the API (JaCoCo), sampled every few seconds |
| `started.txt`, `completed.txt`, `verified.txt` | Progress of the run |

After `analyze-data`, `results/time_budget_aggregated_results_<date>.csv` contains the results of all analyzed runs,
one row per run (e.g., to open with a spreadsheet).

### Watching a run

- `docker ps` lists the containers of the running experiment: `restgym--perseverest-for-<api>--run-<id>`
  (PerseveREST), `restgym--<api>-for-perseverest--run-<id>` (the API) and RESTgym's launcher.
- `docker logs -f restgym--perseverest-for-<api>--run-<id>` shows the log of PerseveREST while it runs (e.g.,
  `PerseveREST: round 12 started`).
- `docker stats` shows the CPU and memory used by each container.

### Notes

- Some APIs need more resources than others: at the end of a 60-minute run on `flight-search`, RESTgym reads the
  whole log of the API (several GB) into memory, which failed on our machine with 7.6 GB of RAM for Docker (the
  results of the run were complete, but `completed.txt` was missing); `genome-nexus` needs more than 5 GB of RAM to start.
- `gestao-hospital` geocodes the addresses of hospitals with an external service (LocationIQ), with a key shared by
  everyone who runs the API: when its daily quota is used up, hospitals cannot be created, whatever the tool.

### How the image is built

The `Dockerfile` compiles the source code in `source/` (Gradle 8.14, Java 17; the build downloads the dependencies
from Maven Central) and runs PerseveREST on `eclipse-temurin:24-jre-alpine`. At run time the tool reads `API`,
`HOST` and `PORT`, and the specification in `/specifications/$API-openapi.json` (or `/specifications/$API.yaml`);
all requests go to `http://$HOST:$PORT`.

## Source code and tests

`source/` contains RestTestGen 25.12 with PerseveREST's changes (see `NOTICE` for the list of added and modified
files). The unit tests of PerseveREST's changes run in `source/` with:

```
gradle test --tests '*TestPerseveRESTStrategy' --tests '*TestResourceCountLimiter' --tests '*TestResourceIdMemory' \
    --tests '*TestCombinedSchemaResolver' --tests '*TestCombinedSchemasPetClinic' --tests '*TestMalformedRequestSender' \
    --tests '*TestTokenInteractionProcessor' --tests '*TestNominalFuzzerOptionalLeaves' --tests '*TestExtendedRandom' \
    --tests '*TestBoundedDictionary' --tests '*TestPrimitiveRequestBody' --tests '*TestFieldVariantSender'
```

## Authors

<AUTHOR NAMES, AFFILIATION, CONTACT EMAIL>

## License

Apache License 2.0 (see `LICENSE` and `NOTICE`).
