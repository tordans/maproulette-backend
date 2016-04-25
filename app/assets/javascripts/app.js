/**
 * Created by cuthbertm on 3/21/16.
 */
marked.setOptions({
    gfm: true,
    tables: true,
    breaks: false,
    pedantic: false,
    sanitize: true,
    smartLists: true,
    smartypants: true
});

toastr.options = {
    "positionClass": "toast-top-center",
    "toastClass": "notification",
    "closeButton": false,
    "debug": false,
    "newestOnTop": false,
    "progressBar": false,
    "preventDuplicates": false,
    "onclick": null,
    "showDuration": "300",
    "hideDuration": "1000",
    "timeOut": "3000",
    "extendedTimeOut": "0",
    "showEasing": "swing",
    "hideEasing": "linear",
    "showMethod": "fadeIn",
    "hideMethod": "fadeOut",
    "tapToDismiss" : false
};

var ToastUtils = {
    /**
     * Gets the notification class based on the sidebar, whether it is collapsed or not
     *
     * @returns {*}
     */
    getPositionClass: function(positionClass) {
        if (typeof positionClass !== 'undefined') {
            return positionClass;
        } else if (!$("#map").length) {
            return 'toast-top-center';
        } else if ($("#sidebar").width() == 50) {
            return 'notification-position';
        } else {
            return 'notification-position-menuopen';
        }
    },
    showToast: function(type, msg, options) {
        if (typeof options === 'undefined' || options === null) {
            options = {};
        }
        if (typeof options.positionClass === 'undefined') {
            options.positionClass = ToastUtils.getPositionClass();
        }
        toastr[type](msg, '', options);
    },
    Info: function(info, options) {
        if (typeof options === 'undefined' || options === null) {
            options = {};
        }
        options.toastClass = 'notification-info toast-info';
        ToastUtils.showToast('info', '<table><tr><td><i class="fa fa-info-circle fa-2x" style="padding-right: 10px;"></i></td><td>' + info + '</td></tr></table>', options);
    },
    Warning: function(warning, options) {
        ToastUtils.showToast('warning', warning, options);
    },
    Error: function(error, options) {
        ToastUtils.showToast('error', error, options);
    },
    Success: function(msg, options) {
        ToastUtils.showToast('success', msg, options);  
    },
    // handles any javascript errors by popping a toast up at the top.
    handleError: function(error) {
        var jsonMsg = JSON.parse(error.responseText);
        ToastUtils.Error(jsonMsg.status + " : " + jsonMsg.message);
    }
};

// Basic namespace for some Util functions used in this js lib
var Utils = {
    addComma: function (str) {
        return (str.match(/\,\s+$/) || str.match(/in\s+$/)) ? '' : ', ';
    },
    mqResultToString: function (addr) {
        // Convert a MapQuest reverse geocoding result to a human readable string.
        var out, county, town;
        if (!addr || !(addr.town || addr.county || addr.hamlet || addr.state || addr.country)) {
            return 'We are somewhere on earth..';
        }
        out = 'We are ';
        if (addr.city !== null) {
            out += 'in ' + addr.city;
        } else if (addr.town !== null) {
            out += 'in ' + addr.town;
        } else if (addr.hamlet !== null) {
            out += 'in ' + addr.hamlet;
        } else {
            out += 'somewhere in ';
        }
        out += Utils.addComma(out);
        if (addr.county) {
            if (addr.county.toLowerCase().indexOf('county') > -1) {
                out += addr.county;
            } else {
                out += addr.county + ' County';
            }
        }
        out += Utils.addComma(out);
        if (addr.state) {
            out += addr.state;
        }
        out += Utils.addComma(out);
        if (addr.country) {
            if (addr.country.indexOf('United States') > -1) {
                out += 'the ';
            }
            out += addr.country;
        }
        out += '.';
        return out;
    },
    getQSParameterByName: function(name) {
        name = name.replace(/[\[\]]/g, "\\$&");
        var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)", "i"),
            results = regex.exec(window.location.href);
        if (!results) return null;
        if (!results[2]) return '';
        return decodeURIComponent(results[2].replace(/\+/g, " "));
    },
    appendQueryString: function(url) {
        var qs = document.URL.split("?");
        var queryString = "";
        if (qs.length > 1) {
            queryString = qs[1];
        }    
        return url + "?" + queryString;
    }
};

/**
 * Helper for the generic delete function, this will delete a specific project
 * Only an Admin user for the specific project will be able to delete the project or Super user
 * 
 * @param itemId The id of the project
 */
var deleteProject = function(itemId) {
    deleteItem("Project", itemId);
    toastr.success("Project [" + itemId + "] deleted");
};

/**
 * Helper for the generic delete function, this will delete a specific challenge
 * Only an Admin user for the specific parent project will be able to delete the challenge or Super user
 * 
 * @param itemId The id of the challenge
 */
var deleteChallenge = function(itemId) {
    deleteItem("Challenge", itemId);
    toastr.success("Challenge [" + itemId + "] deleted");
};

/**
 * Helper for the generic delete function, this will delete a specific survey
 * Only an Admin user for the specific parent project will be able to delete the survey or Super user
 * 
 * @param itemId The id of the survey
 */
var deleteSurvey = function(itemId) {
    deleteItem("Survey", itemId);
    toastr.success("Survey [" + itemId + "] deleted");
};

/**
 * Helper for the generic delete function, this will delete a specific task
 * Only an Admin user for the specific parent project will be able to delete the task or Super user
 * 
 * @param itemId The id of the task
 */
var deleteTask = function(itemId) {
    deleteItem("Task", itemId);
    toastr.success("Task [" + itemId + "] deleted");
};

/**
 * Helper for the generic delete function, this will delete a specific user
 * Only a super user can delete users
 * 
 * @param itemId The id of the user
 */
var deleteUser = function(itemId) {
    deleteItem("User", itemId);
    toastr.success("User [" + itemId + "] deleted");
};

/**
 * The generic delete function that makes the API request to the server
 * 
 * @param itemType The type of object you are deleting
 * @param itemId The id of the object you are deleting
 */
var deleteItem = function(itemType, itemId) {
    var apiCallback = {
        success : function(data) {
            //todo: probably might want to do something with the data
            location.reload();
        },
        error : function(error) {
            location.href = jsRoutes.controllers.Application.error(error.toString());
        }
    };

    if (itemType == "Project") {
        jsRoutes.org.maproulette.controllers.api.ProjectController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "Challenge") {
        jsRoutes.org.maproulette.controllers.api.ChallengeController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "Survey") {
        jsRoutes.org.maproulette.controllers.api.SurveyController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "Task") {
        jsRoutes.org.maproulette.controllers.api.TaskController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "User") {
        jsRoutes.AuthController.deleteUser(itemid).ajax(apiCallback);
    }
};

/**
 * Helper function to specifically find projects, see generic find function for information on parameters
 */
var findProjects = function(search, limit, offset, onlyEnabled, handler, errorHandler) {
    findItem("Project", search, limit, offset, onlyEnabled, handler, errorHandler);  
};

/**
 * Helper function to specifically find challenges, see generic find function for information on parameters
 */
var findChallenges = function(search, limit, offset, onlyEnabled, handler, errorHandler) {
    findItem("Challenge", search, limit, offset, onlyEnabled, handler, errorHandler);
};

/**
 * Helper function to specifically find surveys, see generic find function for information on parameters
 */
var findSurveys = function(search, limit, offset, onlyEnabled, handler, errorHandler) {
    findItem("Survey", search, limit, offset, onlyEnabled, handler, errorHandler);
};

/**
 * Helper function to specifically find tags, see generic find function for information on parameters
 */
var findTags = function(search, limit, offset, handler, errorHandler) {
    findItem("Tag", search, limit, offset, true, handler, errorHandler);
};

/**
 * A generic find function that will make an API call to the server to search for a specific object 
 * and return a list of objects that match the search criteria
 * 
 * @param itemType The type of object you are searching for
 * @param search The search phrase that will match against the name of the object
 * @param limit The number of objects you want returned in the array at one time
 * @param offset A paging mechanism, starting at 0
 * @param onlyEnabled If true then only enabled objects will be returned
 * @param handler The javascript handler that will handle the results
 * @param errorHandler The javascript handler that will handle any failure
 */
var findItem = function(itemType, search, limit, offset, onlyEnabled, handler, errorHandler) {
    var apiCallback = {
        success: handler,
        error: errorHandler
    };
    if (itemType == "Project") {
        jsRoutes.org.maproulette.controllers.api.ProjectController.find(search, limit, offset, onlyEnabled).ajax(apiCallback);
    } else if (itemType == "Challenge") {
        jsRoutes.org.maproulette.controllers.api.ChallengeController.find(search, limit, offset, onlyEnabled).ajax(apiCallback);
    } else if (itemType == "Survey") {
        jsRoutes.org.maproulette.controllers.api.SurveyController.find(search, limit, offset, onlyEnabled).ajax(apiCallback); 
    } else if (itemType == "Tag") {
        jsRoutes.org.maproulette.controllers.api.TagController.getTags(search, limit, offset).ajax(apiCallback);
    }
};

/**
 * Adds a task to the map, if the map is not on the current page it will load that page by 
 * redirecting, if it is on the page then it will simply load it dynamically.
 * 
 * @param parentId
 * @param taskId
 */
var addItemToMap = function(parentId, taskId) {
    if ($("#map").length) {
        MRManager.addTaskToMap(parentId, taskId);    
    } else {
        if (typeof taskId === 'undefined' || taskId == -1) {
            location.href = Utils.appendQueryString("/map/" + parentId);
        } else {
            location.href = Utils.appendQueryString("/map/" + parentId + "/" + taskId);
        }
    }
};

var generateAPIKey = function() {
    var apiCallback = {
        success : function(data) {
            currentAPIKey = data;
            showAPIKey();
        },
        error : Utils.handleError
    };
    jsRoutes.controllers.AuthController.generateAPIKey().ajax(apiCallback);
};

var showAPIKey = function() {
    toastr.info(currentAPIKey);
};
