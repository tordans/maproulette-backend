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
    var map = L.map('map');

    var osmURL = 'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
    var osmAttrib = 'Map Data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors';
    var osm = new L.TileLayer(osmURL, {attribution: osmAttrib});

    map.setView(new L.LatLng(47.6097, -122.3331), 13);
    map.addLayer(osm);
})();

