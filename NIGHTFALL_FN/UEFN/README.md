# Creator-owned UEFN integration

The WPF desktop client cannot read live chest state from Fortnite or control live matchmaking.

## Chest Debug markers

The supplied Verse device toggles a list of map-authored map_indicator_device instances.

Setup:
1. Add the Verse file to your own UEFN project and compile it there.
2. Place one map_indicator_device at each chest location you want marked.
3. Place a button_device in a private testing area.
4. Add the button and marker devices to the Verse device's editable properties in the Details panel.
5. Trigger the button to show or hide the markers.

These are fixed, creator-authored location markers. They do not detect whether a chest currently contains loot, and they do not scan the Fortnite process. Anyone who can interact with the configured button can toggle the markers, so place/restrict that button appropriately for your private testing session. Player ESP is not part of this project.

Epic documents map_indicator_device as a custom point-of-interest marker on the minimap and overview map. Its Verse API includes Enable and Disable.

## Private Map Queue

Queue behavior is configured through Fortnite Creative / UEFN Island and Mode Settings. Queue-related values include maximum players, minimum players, overtime target, main duration, overtime duration and matchmaking privacy.

NIGHTFALL FN includes an editable local profile for those values. Apply the actual settings in the island project. The desktop client cannot push settings into Fortnite or automatically enter matchmaking.

Official documentation:
- https://dev.epicgames.com/documentation/en-us/fortnite/matchmaking-queue-controls-in-fortnite-creative
- https://dev.epicgames.com/documentation/en-us/fortnite/mode-settings-in-fortnite-creative
- https://dev.epicgames.com/documentation/en-us/fortnite/verse-api/fortnitedotcom/devices/map_indicator_device
- https://dev.epicgames.com/documentation/en-us/fortnite/coding-device-interactions-in-verse
