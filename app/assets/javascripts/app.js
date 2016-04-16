/**
 * Created by cuthbertm on 3/21/16.
 */
// Basic namespace for some Util functions used in this js lib
var Utils = {
    // handles any javascript errors by popping a toast up at the top.
    handleError: function(error) {
        var jsonMsg = JSON.parse(error.responseText);
        toastr.error(jsonMsg.status + " : " + jsonMsg.message);
    },
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
    }
};

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

var deleteProject = function(itemId) {
    deleteItem("Project", itemId);
    toastr.success("Project [" + itemId + "] deleted");
};

var deleteChallenge = function(itemId) {
    deleteItem("Challenge", itemId);
    toastr.success("Challenge [" + itemId + "] deleted");
};

var deleteSurvey = function(itemId) {
    deleteItem("Survey", itemId);
    toastr.success("Survey [" + itemId + "] deleted");
};

var deleteTask = function(itemId) {
    deleteItem("Task", itemId);
    toastr.success("Task [" + itemId + "] deleted");
};

var deleteUser = function(itemId) {
    deleteItem("User", itemId);
    toastr.success("User [" + itemId + "] deleted");
};

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
