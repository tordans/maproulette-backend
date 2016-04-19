toastr.options.toastClass = "notification";
toastr.options.positionClass = "notification-position";

L.TileLayer.Common = L.TileLayer.extend({
    initialize: function (options) {
        L.TileLayer.prototype.initialize.call(this, this.url, options);
    }
});

// -- CUSTOM CONTROLS ----------------------------------
// Help control, when you click on this the help screen will overlay the map
L.Control.Help = L.Control.extend({
    options: {
        position: 'topright',
        text: "Help Text"
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-control mp-control-component');
        var control = L.DomUtil.create('a', 'fa fa-question fa-2x', container);
        control.href = "#";
        var text = L.DomUtil.create('span', '', container);
        text.innerHTML = " Help";
        var self = this;
        L.DomEvent.on(container, 'click', L.DomEvent.stopPropagation)
            .on(container, 'click', L.DomEvent.preventDefault)
            .on(container, 'click', function (e) {
                $("#infoText").innerHTML = marked(self.options.text);
                $("#information").modal({backdrop:false});
            });
        return container;
    }
});

// Control panel for all the task functions
L.Control.ControlPanel = L.Control.extend({
    options: {
        position: 'bottomright',
        controls:[false, false, false, false],
        showText:true,
        signedIn:false,
    },
    onAdd: function(map) {
        // we create all the containers first so that the ordering will always be consistent
        // no matter what controls a user decides to put on the map
        var container = L.DomUtil.create('div', 'mp-control');
        container.id = "controlpanel_container";
        var prevDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        prevDiv.id = "controlpanel_previous";
        var editDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        editDiv.id = "controlpanel_edit";
        var fpDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        fpDiv.id = "controlpanel_fp";
        var nextDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        nextDiv.id = "controlpanel_next";
        return container;
    },
    // updates the controls, removes and adds if necessary
    update: function(signedIn, prevControl, editControl, fpControl, nextControl) {
        this.options.signedIn = signedIn;
        this.options.controls[0] = prevControl;
        this.options.controls[1] = editControl;
        this.options.controls[2] = fpControl;
        this.options.controls[3] = nextControl;
        this.updateControls();
    },
    // generic function to update the controls on the map
    updateControl: function(controlID, controlName, friendlyName, icon, locked, clickHandler) {
        if (this.options.controls[controlID]) {
            var controlDiv = L.DomUtil.get(controlName);
            if (!controlDiv.hasChildNodes()) {
                var control = L.DomUtil.create('a', 'fa ' + icon + ' fa-2x', controlDiv);
                control.href = "#";
                var text = L.DomUtil.create('span', '', controlDiv);
                if (this.options.showText) {
                    text.innerHTML = " " + friendlyName;
                }
                L.DomEvent.on(controlDiv, 'click', L.DomEvent.stopPropagation)
                    .on(controlDiv, 'click', L.DomEvent.preventDefault)
                    .on(controlDiv, 'click', clickHandler);
            }
            if (locked) {
                L.DomUtil.addClass(controlDiv, "mp-control-locked");
            } else {
                L.DomUtil.removeClass(controlDiv, "mp-control-locked");
            }
        } else {
            $("#" + controlName).innerHTML = "";
        }

    },
    updateControls: function() {
        this.updatePreviousControl();
        this.updateEditControl();
        this.updateFPControl();
        this.updateNextControl();
    },
    updatePreviousControl: function() {
        this.updateControl(0, "controlpanel_previous", "Previous", "fa-backward", false, function(e) {
            MRManager.getPreviousTask();
        });
    },
    updateEditControl: function() {
        this.updateControl(1, "controlpanel_edit", "Edit", "fa-pencil",
            MRManager.isTaskLocked() || !this.options.signedIn, function(e) {
            if (!locked) {
                $("#editoptions").fadeIn('slow');
            }
        });
    },
    updateFPControl: function() {
        this.updateControl(2, "controlpanel_fp", "False Positive", "fa-warning",
            MRManager.isTaskLocked() || !this.options.signedIn, function(e) {
            if (!locked) {
                MRManager.setTaskStatus(TaskStatus.FALSEPOSITIVE);
            }
        });
    },
    updateNextControl: function() {
        this.updateControl(3, "controlpanel_next", "Next", "fa-forward", false, function(e) {
            MRManager.getNextTask();
        });
    }
});
// -----------------------------------------------------

// add various basemap layers to the TileLayer namespace
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

// A simple point class
function Point(x, y) {
    this.x = x;
    this.y = y;
}

var TaskStatus = {
    FIXED:1,
    FALSEPOSITIVE:2,
    SKIPPED:3,
    DELETED:4,
    ALREADYFIXED:5,
    CREATED:0,
    AVAILABLE:6
};

/**
 * The Task class contains all relevent information regarding the current task loaded on the map.
 * Also contains functionality to get next, previous and random tasks.
 *
 * @param data This is the task data that would be in the form
 * {
 *  "id":<TASK_ID>,
 *  "parentId":<CHALLENGE_ID>,
 *  "name":"<NAME>",
 *  "instruction":"<INSTRUCTION>",
 *  "status":"<STATUS>",
 *  "geometry":"<GEOJSON>"
 *  }
 * @constructor
 */
function Task(data) {
    this.parentData = {};
    this.data = data;

    this.resetTask = function() {
        this.data = {id:-1};
    };

    this.isLocked = function() {
        return this.data.locked || this.data.status == TaskStatus.FIXED ||
            this.data.status == TaskStatus.FALSEPOSITIVE || this.data.status == TaskStatus.DELETED ||
            this.data.status == TaskStatus.ALREADYFIXED;
    };

    this.updateTask = function(taskId, success, error) {
        var self = this;
        jsRoutes.controllers.MappingController.getTaskDisplayGeoJSON(taskId).ajax({
            success:function(data) {
                self.data = data;
                self.getSuccessHandler(success)();
            },
            error:error
        });
    };

    this.getNextTask = function(params, success, error) {
        var self = this;
        jsRoutes.controllers.MappingController
            .getSequentialNextTask(this.data.parentId, this.data.id)
            .ajax({
                success:function(data) {
                    self.data = data;
                    self.getSuccessHandler(success)();
                },
                error:this.getErrorHandler(error)
            });
    };

    this.getPreviousTask = function(params, success, error) {
        var self = this;
        jsRoutes.controllers.MappingController
            .getSequentialPreviousTask(this.data.parentId, this.data.id)
            .ajax({
                success: function (data) {
                    self.data = data;
                    self.getSuccessHandler(success)();
                },
                error:this.getErrorHandler(error)
            });
    };

    this.getRandomNextTask = function(params, success, error) {
        var self = this;
        jsRoutes.controllers.MappingController.getRandomNextTask(params.projectId,
            params.projectSeach,
            params.challengeId,
            params.challengeSearch,
            params.challengeTags,
            params.taskSearch,
            params.taskTags)
            .ajax({
                success:function(data) {
                    self.data = data;
                    self.getSuccessHandler(success)();
                },
                error:this.getErrorHandler(error)
            }
        );
    };
    
    this.setTaskStatus = function(status, params, success, error) {
        var self = this;
        var errorHandler = this.getErrorHandler(error);
        var statusSetSuccess = function () {
            self.getRandomNextTask(params, self.getSuccessHandler(success), errorHandler);
        };
        switch (status) {
            case TaskStatus.FIXED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusFixed(this.data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.FALSEPOSITIVE:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusFalsePositive(this.data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.SKIPPED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusSkipped(this.data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.DELETED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusDeleted(this.data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.ALREADYFIXED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusAlreadyFixed(this.data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
        }
    };
    
    this.getSuccessHandler = function(success) {
        if (typeof success === 'undefined') {
            return MRManager.updateDisplayTask;
        } else {
            return success;
        }
    };
    
    this.getErrorHandler = function(error) {
        if (typeof error === 'undefined') {
            return Utils.handleError;
        } else {
            return error;
        }
    };
}

/**
 * The search parameters allow tasks to be executed over multiple different projects, challenges
 * and tags. It allows searching of those elements and the next random task retrieved will remain
 * in the bounds of the search parameters.
 *
 * projectId If wanting to limit to a specific project set this value
 * projectSearch Will filter based on the name of the project
 * challengeId If wanting to limit to a specific challenge set this value
 * challengeSearch Will filter based on the name of the challenge. eg. All challenges starting with "c"
 * challengeTags Will filter based on the supplied tags for the challenge
 * taskSearch Will filter based on the name of the task (probably wouldn't be used a lot
 * taskTags Will filter based on the tags of the task
 * @constructor
 */
function SearchParameters() {
    this.projectId = Utils.getQSParameterByName("pid");
    this.projectSeach = Utils.getQSParameterByName("ps");
    this.challengeId = Utils.getQSParameterByName("cid");
    this.challengeSearch = Utils.getQSParameterByName("cs");
    var ctQS = Utils.getQSParameterByName("ct");
    if (ctQS === null) {
        this.challengeTags = null;
    } else {
        this.challengeTags = ctQS.split(",");
    }
    this.taskSearch = Utils.getQSParameterByName("s");
    var tagsQS = Utils.getQSParameterByName("tags");
    if (tagsQS === null) {
        this.taskTags = null;
    } else {
        this.taskTags = tagsQS.split(",");
    }
    this.getQueryString = function() {
        return "pid="+this.projectId+"&ps="+this.projectSeach+"&cid="+this.challengeId+"&cs="+this.challengeSearch+"&ct="+this.challengeTags+"&s="+this.taskSearch+"&tags="+this.taskTags;
    };
}

var MRManager = (function() {
    var map;
    var geojsonLayer;
    var layerControl;
    // controls
    var controlPanel = new L.Control.ControlPanel({});
    var currentTask = new Task({id:-1});
    // In debug mode tasks will not be edited and the previous button is displayed in the control panel
    var debugMode = Boolean(Utils.getQSParameterByName("debug"));
    var currentSearchParameters = new SearchParameters();
    var signedIn = false;

    // Function that handles the resizing of the map when the menu is toggled
    var resizeMap = function() {
        var mapDiv = $("#map");
        var notifications = $(".notification-position");
        var menuOpenNotifications = $(".notification-position-menuopen");
        var sidebarWidth = $("#sidebar").width();
        if (sidebarWidth == 50) {
            mapDiv.animate({left: '230px'});
            notifications.animate({left: '270px'});
            menuOpenNotifications.animate({left: '270px'});
        } else if (sidebarWidth == 230) {
            mapDiv.animate({left: '50px'});
            notifications.animate({left: '90px'});
            menuOpenNotifications.animate({left: '90px'});
        }
    };

    var init = function (userSignedIn, element, point) {
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
        layerControl = L.control.layers(
            {'OSM': osm_layer, 'Open Cycle': opencycle_layer, 'MapQuest Roads': road_layer,
                'MapQuest': mapquest_layer, 'Bing': bing_layer},
            {'GeoJSON': geojsonLayer},
            {position:"topright"}
        );
        map.addControl(new L.Control.Help({}));
        map.addControl(layerControl);
        map.addControl(controlPanel);

        // handles click events that are executed when submitting the custom geojson from the geojson viewer
        $('#geojson_submit').on('click', function() {
            if ($('#geojson_text').val().length < 1) {
                $('#geoJsonViewer').modal("hide");
                return;
            }
            geojsonLayer.clearLayers();
            geojsonLayer.addData(JSON.parse($('#geojson_text').val()));
            map.fitBounds(geojsonLayer.getBounds());
            $('#geoJsonViewer').modal("hide");
            // in this scenario the task needs to be reset
            currentTask.resetTask();
            controlPanel.updateUI(false, false, false, false);
        });
        // handles the click event from the sidebar toggle
        $("#sidebar_toggle").on("click", resizeMap);
        $("#map").css("left", $("#sidebar").width());
        signedIn = userSignedIn;
    };

    /**
     * Updates the map, this will first remove all current layers and then update the map with
     * the current task geometry
     */
    var updateTaskDisplay = function() {
        geojsonLayer.clearLayers();
        geojsonLayer.addData(currentTask.data.geometry);
        map.fitBounds(geojsonLayer.getBounds());
        controlPanel.update(signedIn, debugMode, true, true, true);
        // show the task text as a notification
        toastr.clear();
        toastr.info(marked(currentTask.data.instruction), '', { positionClass: getNotificationClass(), timeOut: 0 });
        // let the user know where they are
        displayAdminArea();
    };

    /**
     * Gets the notification class based on the sidebar, whether it is collapsed or not
     *
     * @returns {*}
     */
    var getNotificationClass = function() {
        if ($("#sidebar").width() == 50) {
            return 'notification-position';
        } else {
            return 'notification-position-menuopen';
        }
    };

    /**
     * Will display the current location that you are working in
     */
    var displayAdminArea = function () {
        var mqurl = 'http://open.mapquestapi.com/nominatim/v1/reverse.php?key=Nj8oRSldMF8mjcsqp2JtTIcYHTDMDMuq&format=json&lat=' + map.getCenter().lat + '&lon=' + map.getCenter().lng;
        $.ajax({
            url: mqurl,
            jsonp: "json_callback",
            success: function (data) {
                toastr.info(Utils.mqResultToString(data.address), '', {positionClass: getNotificationClass()});
            }
        });
    };

    /**
     * This function generally will only be called on a page load. So the initial entry into the
     * mapping area
     *
     * @param parentId This would be the id for a challenge, can be ignored if you are supplying the Task ID
     * @param taskId The taskId if you are looking for a specific task
     */
    var addTaskToMap = function(parentId, taskId) {
        if (typeof taskId === 'undefined' || taskId == -1) {
            currentSearchParameters.challengeId = parentId;
            currentTask.getRandomNextTask(currentSearchParameters);   
        } else {
            currentTask.updateTask(taskId);
        }
    };

    /**
     * Gets the Next Task within the current search parameters. This function in debug mode will
     * retrieve the next task sequentially, in non-debug mode it will retrieve the next random task
     */
    var getNextTask = function() {
        if (debugMode) {
            currentTask.getNextTask(currentSearchParameters);
        } else {
            currentTask.getRandomNextTask(currentSearchParameters);
        }
    };

    /**
     * Gets the previous Task within the current search parameters, this function is only available
     * when in Debug Mode.
     */
    var getPreviousTask = function() {
        if (debugMode) {
            currentTask.getPreviousTask(currentSearchParameters);
        }
    };
    
    var setTaskStatus = function(status) {
        currentTask.setTaskStatus(status, currentSearchParameters);
    };

    // registers a series of hotkeys for quick access to functions
    var registerHotKeys = function() {
        $(document).keydown(function(e) {
            e.preventDefault();
            switch(e.keyCode) {
                case 81: //q
                    // Get next task, set current task to false positive
                    break;
                case 87: //w
                    // Get next task, skip current task
                    break;
                case 69: //e
                    // open task in ID
                    break;
                case 82: //r
                    // open task in JSOM in current layer
                    break;
                case 84: //y
                    // open task in JSOM in new layer
                    break;
                case 27: //esc
                    // remove open dialog
                    break;
                default:
                    break;
            }
        });
    };
    
    // This funtion returns the geoJSON of the currently displayed Task
    var getCurrentTaskGeoJSON = function() {
        if (currentTask.data.id == -1) {
            return "{}";
        }
        return JSON.stringify(currentTask.data.geometry);
    };

    var isTaskLocked = function() {
        return currentTask.isLocked();
    };

    return {
        init: init,
        addTaskToMap: addTaskToMap,
        updateDisplayTask: updateTaskDisplay,
        getCurrentTaskGeoJSON: getCurrentTaskGeoJSON,
        getNextTask: getNextTask,
        getPreviousTask: getPreviousTask,
        setTaskStatus: setTaskStatus,
        isTaskLocked: isTaskLocked
    };

}());
