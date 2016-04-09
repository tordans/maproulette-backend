L.TileLayer.Common = L.TileLayer.extend({
    initialize: function (options) {
        L.TileLayer.prototype.initialize.call(this, this.url, options);
    }
});

(function () {

    var osmAttr = '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>';

    L.TileLayer.CloudMade = L.TileLayer.Common.extend({
        url: 'http://{s}.tile.cloudmade.com/{key}/{styleId}/256/{z}/{x}/{y}.png',
        options: {
            attribution: 'Map data ' + osmAttr + ', Imagery &copy; <a href="http://cloudmade.com">CloudMade</a>',
            styleId: 997
        }
    });

    L.TileLayer.OpenStreetMap = L.TileLayer.Common.extend({
        url: 'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        options: {attribution: osmAttr}
    });

    L.TileLayer.OpenCycleMap = L.TileLayer.Common.extend({
        url: 'http://{s}.tile.opencyclemap.org/cycle/{z}/{x}/{y}.png',
        options: {
            attribution: '&copy; OpenCycleMap, ' + 'Map data ' + osmAttr
        }
    });

    var mqTilesAttr = 'Tiles &copy; <a href="http://www.mapquest.com/" target="_blank">MapQuest</a> <img src="http://developer.mapquest.com/content/osm/mq_logo.png" />';

    L.TileLayer.MapQuestOSM = L.TileLayer.Common.extend({
        url: 'http://otile{s}.mqcdn.com/tiles/1.0.0/{type}/{z}/{x}/{y}.png',
        options: {
            subdomains: '1234',
            type: 'osm',
            attribution: 'Map data ' + L.TileLayer.OSM_ATTR + ', ' + mqTilesAttr
        }
    });

    L.TileLayer.MapQuestAerial = L.TileLayer.MapQuestOSM.extend({
        options: {
            type: 'sat',
            attribution: 'Imagery &copy; NASA/JPL-Caltech and U.S. Depart. of Agriculture, Farm Service Agency, ' + mqTilesAttr
        }
    });

    L.TileLayer.MapBox = L.TileLayer.Common.extend({
        url: 'http://{s}.tiles.mapbox.com/v3/{user}.{map}/{z}/{x}/{y}.png'
    });

    L.TileLayer.Bing = L.TileLayer.Common.extend({
        url: 'http://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
    });

}());

var Point = function(x, y) {
    this.x = x;
    this.y = y;
};

var MRManager = (function() {
    var map;
    var geojsonLayer;

    var init = function (element, point) {
        var osm_layer = new L.TileLayer.OpenStreetMap(),
            road_layer = new L.TileLayer.MapQuestOSM(),
            mapquest_layer = new L.TileLayer.MapQuestAerial(),
            opencycle_layer = new L.TileLayer.OpenCycleMap(),
            bing_layer = new L.TileLayer.Bing();
        map = new L.Map(element, {
            center: new L.LatLng(point.x, point.y),
            zoom: 13,
            layers: [
                osm_layer
            ]
        });

        geojsonLayer = new L.GeoJSON(null, {
            onEachFeature: function (feature, layer) {
                if (feature.properties) {
                    var counter = 0;
                    var popupString = '<div class="popup">';
                    for (var k in feature.properties) {
                        counter++;
                        var v = feature.properties[k];
                        popupString += k + ': ' + v + '<br />';
                    }
                    popupString += '</div>';
                    if (counter > 0) {
                        layer.bindPopup(popupString, {
                            maxHeight: 200
                        });
                    }
                }
            }
        });

        map.addLayer(geojsonLayer);

        L.control.layers(
            {'OSM': osm_layer, 'Open Cycle': opencycle_layer, 'MapQuest Roads': road_layer,
                'MapQuest': mapquest_layer, 'Bing': bing_layer},
            {'GeoJSON': geojsonLayer},
            {position:"topright"}
        ).addTo(map);

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
    };

    // Adds default controls like challenge/survey information to the map
    var addDefaultControls = function() {

    };

    // Adds any challenge specific controls to the map
    var addChallengeControls = function() {

    };

    // Adds any survey specific controls to the map
    var addSurveyControls = function() {

    };

    var addTaskToMap = function(parentId, taskId, parentType) {
        var apiCallback = {
            success : function(data) {
                geojsonLayer.clearLayers();
                geojsonLayer.addData(data);
                map.fitBounds(geojsonLayer.getBounds());
                addDefaultControls();
                if (parentType === "Challenge") {
                    addChallengeControls();
                } else {
                    addSurveyControls();
                }
            },
            error : function(error) {
                toastr.error(error);
            }
        };

        if (parentId != -1) {
            if (taskId != -1) {
                jsRoutes.controllers.MappingController.getTaskDisplayGeoJSON(taskId).ajax(apiCallback);
            } else {
                
            }
        }
    };

    return {
        init: init,
        addTaskToMap: addTaskToMap
    };
}());
