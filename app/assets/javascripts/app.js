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
    "closeButton": false,
    "debug": false,
    "newestOnTop": false,
    "progressBar": false,
    "toastClass": "notification",
    "positionClass": "notification-position",
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
};

var deleteChallenge = function(itemId) {
    deleteItem("Challenge", itemId);
};

var deleteSurvey = function(itemId) {
    deleteItem("Survey", itemId);
};

var deleteTask = function(itemId) {
    deleteItem("Task", itemId);
};

var deleteUser = function(itemId) {
    deleteItem("User", itemId);
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
        error : function(error) {
            toastr.error(error);
        }
    };

    jsRoutes.controllers.AuthController.generateAPIKey().ajax(apiCallback);
};

var showAPIKey = function() {
    toastr.info(currentAPIKey);
};
