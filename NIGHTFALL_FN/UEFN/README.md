# Creator-owned UEFN integration notes

The WPF desktop client cannot read live chest state from Fortnite or control live matchmaking.

## Chest Debug

For your own island, implement chest markers inside UEFN using map-authored devices and known chest locations. Epic documents:
- map_indicator_device for custom points of interest on the minimap and overview map.
- beacon_device for in-world visual effects and/or HUD markers.

Keep activation and visibility explicit in your island design. This is an island-side debug feature, not an external memory-reading ESP. Player ESP is excluded.

## Private Map Queue

Queue behavior is configured through Fortnite Creative / UEFN Island and Mode Settings. Queue-related values include maximum players, minimum players, overtime target, main duration, overtime duration and matchmaking privacy.

NIGHTFALL FN can store a reference profile for those values. Apply the actual settings in the island project. The desktop client cannot push settings into Fortnite or automatically enter matchmaking.

Official documentation:
- https://dev.epicgames.com/documentation/en-us/fortnite/matchmaking-queue-controls-in-fortnite-creative
- https://dev.epicgames.com/documentation/en-us/fortnite/mode-settings-in-fortnite-creative
- https://dev.epicgames.com/documentation/en-us/fortnite/verse-api/fortnitedotcom/devices/map_indicator_device
- https://dev.epicgames.com/documentation/en-us/fortnite/verse-api/fortnitedotcom/devices
