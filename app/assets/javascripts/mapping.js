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

// Survey Questionaire control
L.Control.SurveyControl = L.Control.extend({
    options: {
       position: 'bottomright'
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-survey-control hidden');
        container.id = "survey_container";
        var questionP = L.DomUtil.create('p', 'mp-control-component', container);
        questionP.id = "survey_question";
        var answerDiv = L.DomUtil.create('div', 'mp-control-component', container);
        answerDiv.id = "survey_answers";
        return container;
    },
    updateSurvey: function(question, answers) {
        L.DomUtil.get("survey_question").innerHTML = question;
        var answerDiv = L.DomUtil.get("survey_answers");
        answerDiv.innerHTML = "";
        for (var i = 0; i < answers.length; i++) {
            this.buildAnswer(answerDiv, answers[i].id, answers[i].answer);
        }
    },
    buildAnswer: function(answerDiv, id, answer) {
        var answerButton = L.DomUtil.create('button', 'btn-xs btn-block btn-default', answerDiv);
        answerButton.innerHTML = answer;
        L.DomEvent.on(answerButton, 'click', L.DomEvent.stopPropagation)
            .on(answerButton, 'click', L.DomEvent.preventDefault)
            .on(answerButton, 'click', function() {
                MRManager.answerSurveyQuestion(id);
            });
    },
    hide: function() {
        L.DomUtil.addClass(L.DomUtil.get("survey_container"), "hidden");
    },
    show: function() {
        L.DomUtil.removeClass(L.DomUtil.get("survey_container"), "hidden");
    }
});

// Edit Control - When user clicks on edit this will show up in the center of the screen
L.Control.EditControl = L.Control.extend({
    options: {
       position: 'bottomright'
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-edit-control hidden');
        container.id = "edit_container";
        var information = L.DomUtil.create('p', 'mp-control-component', container);
        information.id = "edit_information";
        var options = L.DomUtil.create('div', 'mp-control-component', container);
        options.id = "edit_options";
        return container;
    },
    setAsEdit: function() {
        var self = this;
        L.DomUtil.get("edit_information").innerHTML = "Please select how you would like to fix this task.";
        var options = L.DomUtil.get("edit_options");
        options.innerHTML = "";
        var editInID = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInID.innerHTML = "Edit in iD";
        L.DomEvent.on(editInID, 'click', L.DomEvent.stopPropagation)
            .on(editInID, 'click', L.DomEvent.preventDefault)
            .on(editInID, 'click',  MRManager.openTaskInId);

        var editInJOSM = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInJOSM.innerHTML = "Edit in JOSM";
        L.DomEvent.on(editInJOSM, 'click', L.DomEvent.stopPropagation)
            .on(editInJOSM, 'click', L.DomEvent.preventDefault)
            .on(editInJOSM, 'click', MRManager.openTaskInJosm);

        var editInJOSMLayer = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInJOSMLayer.innerHTML = "Edit in new JOSM Layer";
        L.DomEvent.on(editInJOSMLayer, 'click', L.DomEvent.stopPropagation)
            .on(editInJOSMLayer, 'click', L.DomEvent.preventDefault)
            .on(editInJOSMLayer, 'click', function() {
                MRManager.openTaskInJosm(true);
            });

        var closeEdit = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        closeEdit.innerHTML = "Nevermind close this";
        L.DomEvent.on(closeEdit, 'click', L.DomEvent.stopPropagation)
            .on(closeEdit, 'click', L.DomEvent.preventDefault)
            .on(closeEdit, 'click', function() {
                self.hide();
            });
    },
    setAsResult: function() {
        L.DomUtil.get("edit_information").innerHTML = "The area is now loaded in your OSM editor. See if you can fix it, and then return to MapRoulette.<br/><i>Please make sure you save (iD) or upload (JSOM) your work after each fix!<i/>";
        var options = L.DomUtil.get("edit_options");
        options.innerHTML = "";
        var fixed = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        fixed.innerHTML = "I fixed it!";
        L.DomEvent.on(fixed, 'click', L.DomEvent.stopPropagation)
            .on(fixed, 'click', L.DomEvent.preventDefault)
            .on(fixed, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.FIXED);
            });

        var difficult = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        difficult.innerHTML = "Too difficult/Couldn't see";
        L.DomEvent.on(difficult, 'click', L.DomEvent.stopPropagation)
            .on(difficult, 'click', L.DomEvent.preventDefault)
            .on(difficult, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.SKIPPED);
            });

        var error = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        error.innerHTML = "It was not an error";
        L.DomEvent.on(error, 'click', L.DomEvent.stopPropagation)
            .on(error, 'click', L.DomEvent.preventDefault)
            .on(error, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.FALSEPOSITIVE);
            });

        var alreadyFixed = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        alreadyFixed.innerHTML = "Someone beat me to it";
        L.DomEvent.on(alreadyFixed, 'click', L.DomEvent.stopPropagation)
            .on(alreadyFixed, 'click', L.DomEvent.preventDefault)
            .on(alreadyFixed, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.ALREADYFIXED);
            });
    },
    hide: function() {
        MRManager.updateMRControls();
        L.DomUtil.addClass(L.DomUtil.get("edit_container"), "hidden");
    },
    show: function() {
        MRManager.updateMRControls();
        L.DomUtil.removeClass(L.DomUtil.get("edit_container"), "hidden");
    }
});

// Control panel for all the task functions
L.Control.ControlPanel = L.Control.extend({
    options: {
        position: 'bottomright',
        controls:[false, false, false, false],
        showText:true,
        signedIn:false,
        editClick:function() {}
    },
    onAdd: function(map) {
        // we create all the containers first so that the ordering will always be consistent
        // no matter what controls a user decides to put on the map
        var container = L.DomUtil.create('div', 'mp-control');
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
                var text = L.DomUtil.create('a', '', controlDiv);
                text.href = "#";
                if (this.options.showText) {
                    text.innerHTML = " " + friendlyName;
                }
                L.DomEvent.on(controlDiv, 'click', L.DomEvent.stopPropagation)
                    .on(controlDiv, 'click', L.DomEvent.preventDefault)
                    .on(controlDiv, 'click', clickHandler);
            }
            if (locked) {
                L.DomUtil.addClass(controlDiv, "mp-control-component-locked");
            } else {
                L.DomUtil.removeClass(controlDiv, "mp-control-component-locked");
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
        var self = this;
        var locked = MRManager.isTaskLocked() || MRManager.isChallengeSurvey();
        this.updateControl(1, "controlpanel_edit", "Edit", "fa-pencil",
            locked || !this.options.signedIn, function(e) {
            if (!locked) {
                self.options.editClick();
            }
        });
    },
    updateFPControl: function() {
        var locked = MRManager.isTaskLocked() || MRManager.isChallengeSurvey();
        this.updateControl(2, "controlpanel_fp", "False Positive", "fa-warning",
            locked || !this.options.signedIn, function(e) {
            if (!locked) {
                MRManager.setTaskStatus(TaskStatus.FALSEPOSITIVE);
            }
        });
    },
    updateNextControl: function() {
        this.updateControl(3, "controlpanel_next", "Next", "fa-forward", false, function(e) {
            MRManager.getNextTask();
        });
    },
    disableControls: function() {
        L.DomUtil.addClass(L.DomUtil.get("controlpanel_edit"), "mp-control-component-locked");
        L.DomUtil.addClass(L.DomUtil.get("controlpanel_fp"), "mp-control-component-locked");
    },
    enableControls: function() {
        L.DomUtil.removeClass(L.DomUtil.get("controlpanel_edit"), "mp-control-component-locked");
        L.DomUtil.removeClass(L.DomUtil.get("controlpanel_fp"), "mp-control-component-locked");
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
    CREATED:0,
    FIXED:1,
    FALSEPOSITIVE:2,
    SKIPPED:3,
    DELETED:4,
    ALREADYFIXED:5,
    getStatusName:function(status) {
        switch(status) {
            case TaskStatus.CREATED: return "Created";
            case TaskStatus.FIXED: return "Fixed";
            case TaskStatus.FALSEPOSITIVE: return "False Positive";
            case TaskStatus.SKIPPED: return "Skipped";
            case TaskStatus.DELETED: return "Deleted";
            case TaskStatus.ALREADYFIXED: return "Already Fixed";
            default: return "Unknown";
        }
    }
};

/**
 * Challenge class to handle anything related to challenges
 *
 * @constructor
 */
function Challenge() {
    var data = {id:-1};
    this.getData = function() {
        return data;
    };
    
    this.resetTask = function() {
        this.data = {id:-1};
    };

    this.isSurvey = function() {
        return data.challengeType == 4;
    };

    /**
     * Updates this object to the challenge with the provided ID
     *
     * @param challengeId ID of the new challenge
     * @param success handler for successful results
     * @param error handler for failed results
     */
    this.updateChallenge = function(challengeId, success, error) {
        if (data.id != challengeId) {
            jsRoutes.org.maproulette.controllers.api.ChallengeController.getChallenge(challengeId).ajax({
                success: function (update) {
                    if (typeof update.challenge === 'undefined') {
                        data = update;
                        // remove the survey panel from the map
                    } else {
                        data = update.challenge;
                        data.answers = update.answers;
                    }
                    // if it is a survey we need to add the survey control panel to the map
                    MRManager.updateMRControls();
                    if (typeof success != 'undefined') {
                        success();
                    }
                },
                error: function () {
                    if (typeof error != 'undefined') {
                        error();
                    }
                }
            });
        }
    };

    /**
     * Answers a survey question, will do nothing if the current challenge is not a survey
     *
     * @param taskId ID of the task for the answer
     * @param answerId The id for the answer
     * @param success success handler
     * @param error error handler
     */
    this.answerQuestion = function(taskId, answerId, success, error) {
        // See Actions.scala which contains ID's for items. 4 = Survey
        if (this.isSurvey()) {
            ToastUtils.Success("Answered question [" + data.instruction + "]");
            jsRoutes.org.maproulette.controllers.api.SurveyController.answerSurveyQuestion(data.id, taskId, answerId).ajax({
                success: function() {
                    if (typeof success === 'undefined') {
                        MRManager.getNextTask();
                    } else {
                        success();
                    }
                },
                error: MRManager.getErrorHandler(error)
            });
        }
    };
}

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
function Task() {
    var challenge = new Challenge();
    this.getChallenge = function() {
        return challenge;
    };
    var data = {id:-1, parentId:-1};
    this.getData = function() {
        return data;
    };
    this.setSeedData = function(parentId, taskId) {
        if (typeof parentId !== 'undefined') {
            data.parentId = parentId;
        }
        if (typeof taskId !== 'undefined') {
            data.id = taskId;
        }
    };

    var updateData = function(update) {
        data = update;
        if (challenge.getData().id != data.parentId) {
            challenge.updateChallenge(data.parentId);
        }
    };

    this.resetTask = function() {
        data = {id:-1, parentId:-1};
    };

    this.isLocked = function() {
        return data.locked || data.status == TaskStatus.FIXED ||
            data.status == TaskStatus.FALSEPOSITIVE || data.status == TaskStatus.DELETED ||
            data.status == TaskStatus.ALREADYFIXED;
    };

    this.updateTask = function(taskId, success, error) {
        jsRoutes.controllers.MappingController.getTaskDisplayGeoJSON(taskId).ajax({
            success:function(update) {
                updateData(update);
                MRManager.getSuccessHandler(success)();
            },
            error:MRManager.getErrorHandler(error)
        });
    };

    this.getNextTask = function(params, success, error) {
        if (data.parentId == -1 && data.id == -1) {
            ToastUtils.Info('You are in debug mode, select a challenge to debug.');
        } else {
            jsRoutes.controllers.MappingController
                .getSequentialNextTask(data.parentId, data.id)
                .ajax({
                    success: function (update) {
                        updateData(update);
                        MRManager.getSuccessHandler(success)();
                    },
                    error: MRManager.getErrorHandler(error)
                });
        }
    };

    this.getPreviousTask = function(params, success, error) {
        if (data.parentId == -1 && data.id == -1) {
            ToastUtils.Info('You are in debug mode, select a challenge to debug.');
        } else {
            jsRoutes.controllers.MappingController
                .getSequentialPreviousTask(data.parentId, data.id)
                .ajax({
                    success: function (update) {
                        updateData(update);
                        MRManager.getSuccessHandler(success)();
                    },
                    error: MRManager.getErrorHandler(error)
                });
        }
    };

    this.getRandomNextTask = function(params, success, error) {
        jsRoutes.controllers.MappingController.getRandomNextTask(params.projectId,
            params.projectSeach,
            params.challengeId,
            params.challengeSearch,
            params.challengeTags,
            params.taskSearch,
            params.taskTags)
            .ajax({
                success:function(update) {
                    updateData(update);
                    MRManager.getSuccessHandler(success)();
                },
                error:MRManager.getErrorHandler(error)
            }
        );
    };
    
    this.setTaskStatus = function(status, params, success, error) {
        var self = this;
        var errorHandler = MRManager.getErrorHandler(error);
        var statusSetSuccess = function () {
            ToastUtils.Success("Set Task [" + data.name + "] status to " + TaskStatus.getStatusName(status));
            self.getRandomNextTask(params, MRManager.getSuccessHandler(success), errorHandler);
        };
        switch (status) {
            case TaskStatus.FIXED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusFixed(data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.FALSEPOSITIVE:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusFalsePositive(data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.SKIPPED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusSkipped(data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.DELETED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusDeleted(data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
            case TaskStatus.ALREADYFIXED:
                jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatusAlreadyFixed(data.id)
                    .ajax({success: statusSetSuccess, error: errorHandler});
                break;
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
    var currentTask = new Task();
    // controls
    var controlPanel = new L.Control.ControlPanel({
        editClick:function() {
            editPanel.setAsEdit();
            editPanel.show();
            controlPanel.disableControls();
        }
    });
    var surveyPanel = new L.Control.SurveyControl({});
    var editPanel = new L.Control.EditControl({});
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
            minZoom: 3,
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
        map.addControl(surveyPanel);
        map.addControl(editPanel);

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
        //register the keyboard shortcuts
        registerHotKeys();
    };

    /**
     * Updates the map, this will first remove all current layers and then update the map with
     * the current task geometry
     */
    var updateTaskDisplay = function() {
        geojsonLayer.clearLayers();
        geojsonLayer.addData(currentTask.getData().geometry);
        map.fitBounds(geojsonLayer.getBounds());
        controlPanel.update(signedIn, debugMode, true, true, true);
        resetEditControls();
        // show the task text as a notification
        toastr.clear();
        ToastUtils.Info(marked(currentTask.getData().instruction), {timeOut: 0});
        // let the user know where they are
        displayAdminArea();
        updateChallengeInfo(currentTask.getData().parentId);
    };

    var resetEditControls = function() {
        editPanel.setAsEdit();
        editPanel.hide();
        updateMRControls();
    };

    /**
     * Based on the challenge type (4 is Survey) will add or remove the survey panel from the map
     */
    var updateMRControls = function() {
        if (signedIn && !debugMode && currentTask.getChallenge().isSurvey()) {
            surveyPanel.updateSurvey(currentTask.getChallenge().getData().instruction,
                currentTask.getChallenge().getData().answers);
            surveyPanel.show();
        } else {
            surveyPanel.hide();
        }

        if (!signedIn || debugMode || currentTask.getChallenge().isSurvey()) {
            // disable the edit and false positive buttons in control if it is a survey
            controlPanel.disableControls();
        } else {
            controlPanel.enableControls();
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
                ToastUtils.Info(Utils.mqResultToString(data.address));
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
        currentTask.setSeedData(parentId, taskId);
        if (debugMode) {
            currentTask.getNextTask(currentSearchParameters);
        } else if (typeof taskId === 'undefined' || taskId == -1) {
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
        if (!signedIn || debugMode) {
            currentTask.getNextTask(currentSearchParameters);
        } else if (currentTask.getChallenge().isSurvey()) {
            currentTask.getRandomNextTask(currentSearchParameters);
        } else {
            currentTask.setTaskStatus(TaskStatus.SKIPPED, currentSearchParameters);
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

    /**
     * Sets the status for the current task
     *
     * @param status The status to set see Task.scala for information on the status ID's
     */
    var setTaskStatus = function(status) {
        currentTask.setTaskStatus(status, currentSearchParameters);
    };

    /**
     * Answers the question for a survey.
     *
     * @param answerId
     */
    var answerSurveyQuestion = function(answerId) {
        currentTask.getChallenge().answerQuestion(currentTask.getData().id, answerId);
    };

    // registers a series of hotkeys for quick access to functions
    var registerHotKeys = function() {
        $(document).keydown(function(e) {
            e.preventDefault();
            switch(e.keyCode) {
                case 81: //q
                    // Get next task, set current task to false positive
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        setTaskStatus(TaskStatus.FALSEPOSITIVE);
                    }
                    break;
                case 87: //w
                    // Get next task, skip current task
                    MRManager.getNextTask();
                    break;
                case 69: //e
                    // open task in ID
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInId();
                    }
                    break;
                case 82: //r
                    // open task in JSOM in current layer
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInJosm(false);
                    }
                    break;
                case 84: //t
                    // open task in JSOM in new layer
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInJosm(true);
                    }
                    break;
                case 27: //esc
                    // remove open dialog
                    resetEditControls();
                    break;
                default:
                    break;
            }
        });
    };

    var constructIdUri = function () {
        var zoom = map.getZoom();
        var center = map.getCenter();
        var lat = center.lat;
        var lon = center.lng;
        var baseUriComponent = "http://osm.org/edit?editor=id#";
        var idUriComponent = "id=";
        var mapUriComponent = "map=" + [zoom, lat, lon].join('/');
        // http://openstreetmap.us/iD/release/#background=Bing&id=w238383695,w238383626,&desmap=20.00/-77.02271/38.90085
        // https://www.openstreetmap.org/edit?editor=id&way=decode274204300#map=18/40.78479/-111.88787
        // https://www.openstreetmap.org/edit?editor=id#map=19/53.30938/-0.98069
        var currentGeometry = currentTask.getData().geometry;
        for (var i in currentGeometry.features) {
            var feature = currentGeometry.features[i];
            if (!feature.properties.osmid) {
                continue;
            }
            switch (feature.geometry.type) {
                case 'Point':
                    idUriComponent += "n" + feature.properties.osmid + ",";
                    break;
                case 'LineString':
                    idUriComponent += "w" + feature.properties.osmid + ",";
                    break;
                case 'Polygon':
                    idUriComponent += "w" + feature.properties.osmid + ",";
                    break;
                case 'MultiPolygon':
                    idUriComponent += "r" + feature.properties.osmid + ",";
                    break;
            }
        }
        // remove trailing comma - iD won't play ball with it
        idUriComponent = idUriComponent.replace(/,$/, "");
        return baseUriComponent + [idUriComponent, mapUriComponent].join('&');
    };

    var openTaskInId = function () {
        // this opens a new tab and focuses the browser on it.
        // We may want to consider http://stackoverflow.com/a/11389138 to
        // open a tab in the background - seems like that trick does not
        // work in all browsers.
        window.open(constructIdUri(), 'MRIdWindow');
        ToastUtils.Info('Your task is being loaded in iD in a separate tab. Please return here after you completed your fixes!', {timeOut:10000});
        editPanel.setAsResult();
        setTimeout(editPanel.show(), 4000);
    };

    var constructJosmUri = function (new_layer) {
        var bounds = map.getBounds();
        var nodes = [];
        var ways = [];
        var relations = [];
        var sw = bounds.getSouthWest();
        var ne = bounds.getNorthEast();
        var uri = 'http://127.0.0.1:8111/load_and_zoom?left=' + sw.lng + '&right=' + ne.lng + '&top=' + ne.lat + '&bottom=' + sw.lat + '&new_layer=' + (new_layer?'true':'false') + '&select=';
        var selects = [];
        var currentGeometry = currentTask.getData().geometry;
        for (var f in currentGeometry.features) {
            var feature = currentGeometry.features[f];
            if (!feature.properties.osmid) {
                continue;
            }
            switch (feature.geometry.type) {
                case 'Point':
                    selects.push('node' + feature.properties.osmid);
                    break;
                case 'LineString':
                    selects.push('way' + feature.properties.osmid);
                    break;
                case 'Polygon':
                    selects.push('way' + feature.properties.osmid);
                    break;
                case 'MultiPolygon':
                    selects.push('relation' + feature.properties.osmid);
                    break;
            }
        }

        uri += selects.join(',');
        return uri;
    };

    var openTaskInJosm = function (new_layer) {
        new_layer = typeof new_layer !== 'undefined' ? new_layer : true;

        if (map.getZoom() < 14) {
            ToastUtils.Warning("Please zoom in a little so we don\'t have to load a huge area from the API.");
            return false;
        }
        var josmUri = constructJosmUri(new_layer);
        // Use the .ajax JQ method to load the JOSM link unobtrusively and alert when the JOSM plugin is not running.
        $.ajax({
            url: josmUri,
            success: function (t) {
                if (t.indexOf('OK') === -1) {
                    ToastUtils.Error('JOSM remote control did not respond. Do you have JOSM running with Remote Control enabled?');
                } else {
                    editPanel.setAsResult();
                    setTimeout(editPanel.show(), 4000);
                }
            },
            error: function (e) {
                ToastUtils.Error('JOSM remote control did not respond. Do you have JOSM running with Remote Control enabled?');
            }
        });
    };
    
    // This funtion returns the geoJSON of the currently displayed Task
    var getCurrentTaskGeoJSON = function() {
        if (currentTask.getData().id == -1) {
            return "{}";
        }
        return JSON.stringify(currentTask.getData().geometry);
    };

    var isTaskLocked = function() {
        return currentTask.isLocked();
    };

    var isChallengeSurvey = function() {
        return currentTask.getChallenge().isSurvey();
    };

    var getSuccessHandler = function(success) {
        if (typeof success === 'undefined') {
            return MRManager.updateDisplayTask;
        } else {
            return success;
        }
    };

    var getErrorHandler = function(error) {
        if (typeof error === 'undefined') {
            return ToastUtils.handleError;
        } else {
            return error;
        }
    };

    return {
        init: init,
        addTaskToMap: addTaskToMap,
        updateDisplayTask: updateTaskDisplay,
        getCurrentTaskGeoJSON: getCurrentTaskGeoJSON,
        getNextTask: getNextTask,
        getPreviousTask: getPreviousTask,
        setTaskStatus: setTaskStatus,
        isTaskLocked: isTaskLocked,
        getSuccessHandler: getSuccessHandler,
        getErrorHandler: getErrorHandler,
        answerSurveyQuestion: answerSurveyQuestion,
        updateMRControls: updateMRControls,
        isChallengeSurvey: isChallengeSurvey,
        openTaskInId: openTaskInId,
        openTaskInJosm: openTaskInJosm
    };

}());
