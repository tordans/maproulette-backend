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

(function() {
    var map = new ol.Map({
        target: 'map',
        layers: [
            new ol.layer.Group({
                'title': 'Base maps',
                layers: [
                    new ol.layer.Tile({
                        title: 'OSM',
                        type: 'base',
                        source: new ol.source.OSM()
                    }),
                    new ol.layer.Tile({
                        title: 'MapQuest Sat',
                        type: 'base',
                        source: new ol.source.MapQuest({layer: 'sat'})
                    })
                ]
            })
        ],
        view: new ol.View({
            center: ol.proj.fromLonLat([37.41, 8.82]),
            zoom: 4
        }),
        controls: ol.control.defaults({
            attributionOptions: ({
                collapsible: false
            })
        }).extend([
            new ol.control.FullScreen(),
            new ol.control.LayerSwitcher()
        ])
    });
})();

