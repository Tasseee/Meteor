using { /Fortnite.com/Devices }
using { /Verse.org/Simulation }

nightfall_chest_debug := class(creative_device):

    @editable
    ToggleButton : button_device = button_device{}

    @editable
    ChestMarkers : []map_indicator_device = array{}

    var MarkersVisible : logic = false

    OnBegin<override>()<suspends> : void =
        ToggleButton.InteractedWithEvent.Subscribe(ToggleMarkers)
        DisableMarkers()

    ToggleMarkers(Agent : agent) : void =
        if (MarkersVisible?):
            set MarkersVisible = false
            DisableMarkers()
        else:
            set MarkersVisible = true
            EnableMarkers()

    EnableMarkers() : void =
        for (Marker : ChestMarkers):
            Marker.Enable()

    DisableMarkers() : void =
        for (Marker : ChestMarkers):
            Marker.Disable()
