toastr.options = {
    "closeButton": false,
    "debug": false,
    "newestOnTop": false,
    "progressBar": false,
    "toastClass": "notification",
    "positionClass": "toast-top-left",
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

var  onSuccess = function(data) {
    $("#apiKey").html("<small id='apiKey'>API Key:<br/>" + data + "</small>");
};

var onError = function(error) {
    $("#apiKey").html("<small id='apiKey'>No key could be generated: " + error + "</small>");
};

// get URL parameters
// http://stackoverflow.com/a/979995
var Q = (function () {
    // This function is anonymous, is executed immediately and
    // the return value is assigned to Q!
    var i = 0;
    var query_string = {};
    var query = window.location.search.substring(1);
    var vars = query.split('&');
    while (i < vars.length) {
        var pair = vars[i].split('=');
        // If first entry with this name
        if (typeof query_string[pair[0]] === 'undefined') {
            query_string[pair[0]] = pair[1];
            // If second entry with this name
        } else if (typeof query_string[pair[0]] === 'string') {
            query_string[pair[0]] = [query_string[pair[0]], pair[1]];
            // If third or later entry with this name
        } else {
            query_string[pair[0]].push(pair[1]);
        }
        i++;
    }
    return query_string;
}());

var MRConfig = (function () {
    return {
        // the UI strings
        strings: {
            msgNextChallenge: 'Faites vos jeux...',
            msgMovingOnToNextChallenge: 'OK, moving right along...',
            msgZoomInForEdit: 'Please zoom in a little so we don\'t have to load a huge area from the API.'
        },

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

var MRManager = (function () {
    var map;
    var near = (Q.lon && Q.lat) ? {
        'lon': parseFloat(Q.lon),
        'lat': parseFloat(Q.lat)
    } : {};
    var taskLayer = new L.geoJson(null, {
        onEachFeature: function (feature, layer) {
            if (feature.properties && feature.properties.text) {
                layer.bindPopup(feature.properties.text);
                return layer.openPopup();
            }
        }
    });

    /*
     * This function initializes the leaflet map, gets the user location, and loads the first task.
     * A challenge is selected based on the user location, the URL parameter, or on the server side using the stored OSM home location.
     * elem is the div id on the page
     */
    var init = function (elem) {
        // check if the map element exists.
        if (!document.getElementById(elem)) return false;

        // initialize the map
        map = new L.Map(elem, MRConfig.mapOptions);

        var layers = {};

        // add each layer to the map
        for (var tileLayerKey in MRConfig.tileLayers) {
            var tileLayer = MRConfig.tileLayers[tileLayerKey];
            var layer = new L.TileLayer(tileLayer.url, {
                attribution: tileLayer.attribution});
            map.addLayer(layer);
            // add layer to control
            layers[tileLayerKey.replace('_',' ')] = layer;
        }

        // add Layer control to the map
        L.control.layers(layers, null, {position:"topleft"}).addTo(map);

        // Add both the tile layer and the task layer to the map
        map.addLayer(taskLayer);
        geoLocateUser();
    };

    var geoLocateUser = function () {
        // Locate the user and define the event triggers
        map.locate({
            setView: false,
            timeout: 10000,
            maximumAge: 0
        });
        // If the location is found, let the user know, and store.
        map.on('locationfound', function (e) {
            near.lat = parseFloat(e.latlng.lat);
            near.lon = parseFloat(e.latlng.lng);
            toastr.info('We found your location. MapRoulette will try and give you tasks closer to home if they are available.');
        });
        // If the location is not found, meh.
        map.on('locationerror', function (e) {
            console.log('location not found or not permitted: ' + e.message);
        });
    };

    return {
        init: init,
        geoLocateUser: geoLocateUser
    };
}());

// initialization
function init(elemName) {
    MRManager.init(elemName);
}
