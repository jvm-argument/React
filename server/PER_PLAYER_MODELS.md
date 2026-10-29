# Personal models on the test server

Players use `/react model flower baseline`, `/react model flower ultra`, or
`/react model universe beta`. `/react model` displays their current choice.
Permission: `react.server.model` (enabled by default in the server distribution).
Console selection is intentionally unavailable because it has no player context.

The all-models subscription is shared, but personal selections are not. Validation
uses `POST /api/v1/subscriptions/model/validate`, which loads the requested runtime
without modifying the subscription default. Each player has a separate inference
client and cancellation epoch. Switching invalidates their previous requests and
clears their rotation/window buffers without reloading React or changing anyone
else's model. Requests retain normal subscription and rate-limit checks.

Choices are stored atomically by UUID in `plugins/React/player-models.yml` and
survive disconnects and restarts. Do not publish this runtime file. Players without
an explicit selection use the server default from `inference.yml`. A configuration
reload rebuilds personal clients with the current endpoint and subscription key.

`%react_model%`, `%react_model_family%`, and `%react_model_name%` use the player
provided by PlaceholderAPI. A null/console placeholder context uses the server
default. Verbose messages identify the model that actually produced the prediction.

Player and `minecraft:mannequin` attacks activate combat collection. UNIVERSE
resolves mannequin entity IDs on the entity scheduler and retains the entity UUID
for subsequent geometry frames. Packet attacks also activate FLOWER combat for
invulnerable mannequins that do not emit a damage event. Recording sessions must
still be started normally with a LEGIT/CHEAT label; attacks gate capture, they do
not invent a label or create an unsolicited recording. CSV layout, 23 model
features and 64-frame UNIVERSE batches are unchanged.

Install the new `React-Server-2.0.jar` as the sole React core. A normal Minecraft
restart is required; `/react reload` cannot load replaced Java classes. Paper's
`plugins/update/` directory can stage the new core for the next startup. Keep the
previous core for rollback and deploy the backend update before the new plugin.
