toastr.options = {
    "closeButton": true,
    "debug": false,
    "newestOnTop": false,
    "progressBar": false,
    "toastClass": "notification",
    "positionClass": "toast-top-center",
    "preventDuplicates": false,
    "onclick": null,
    "showDuration": "300",
    "hideDuration": "1000",
    "timeOut": "3000",
    "extendedTimeOut": "0",
    "showEasing": "swing",
    "hideEasing": "linear",
    "showMethod": "fadeIn",
    "hideMethod": "fadeOut"
};

var generateAPIKey = function() {
    var apiCallback = {
        success : onSuccess,
        error : onError
    };

    jsRoutes.controllers.AuthController.generateAPIKey().ajax(apiCallback);
};

var showAPIKey = function() {
    toastr.info(currentAPIKey);
};

var  onSuccess = function(data) {
    currentAPIKey = data;
    showAPIKey();
};

var onError = function(error) {
    toastr.error(error);
};

var MRConfig = (function () {
    return {
        // the default map options
        mapOptions: {
            center: new L.LatLng(40, -90),
            zoom: 4,
            keyboard: false
        },

        // the collection of tile layers.
        // Each layer should have an url and attribution property.
        // the key will be the title in the layer picker control
        // where underscores in the key will be translated to spaces.
        tileLayers: {
            OpenStreetMap: {
                url: "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
                attribution: "&copy; <a href=\'http://openstreetmap.org\'> OpenStreetMap</a> contributors"},
            ESRI_Aerial: {
                url: "http://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
                attribution: "Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community"}
        },

        // minimum zoom level for enabling edit buttons
        minZoomLevelForEditing: 14
    };
}());

(function() {
    var map;
    $(document).ready(function() {
        var road_layer = new L.TileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/map/{z}/{x}/{y}.png', {
            maxZoom: 18,
            subdomains: ['1', '2', '3', '4'],
            attribution: 'Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a>. Map data (c) <a href="http://www.openstreetmap.org/" target="_blank">OpenStreetMap</a> contributors, CC-BY-SA.'
        }),
        satellite_layer = new L.TileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.png', {
            maxZoom: 18,
            subdomains: ['1', '2', '3', '4'],
            attribution: 'Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a>.'
        }),
        map = new L.Map('map', {
            center: new L.LatLng(47.6097, -122.3331),
            zoom: 13,
            layers: [
                road_layer
            ]
        });

        var geojsonLayer = new L.GeoJSON(null, {
            onEachFeature: function (feature, layer) {
                if (feature.properties) {
                    var popupString = '<div class="popup">';
                    for (var k in feature.properties) {
                        var v = feature.properties[k];
                        popupString += k + ': ' + v + '<br />';
                    }
                    popupString += '</div>';
                    layer.bindPopup(popupString, {
                        maxHeight: 200
                    });
                }
            }
        });

        map.addLayer(geojsonLayer);

        L.control.layers({'Road': road_layer, 'Satellite': satellite_layer}, {'GeoJSON': geojsonLayer}).addTo(map);

        $('#geojson_submit').on('click', function() {
            if ($('#geojson_text').val().length < 1) {
                $('#geoJsonViewer').modal("hide");
                return;
            }
            geojsonLayer.clearLayers();
            geojsonLayer.addData(JSON.parse($('#geojson_text').val()));
            map.fitBounds(geojsonLayer.getBounds());
            $('#geoJsonViewer').modal("hide");
        });
    });
}());

