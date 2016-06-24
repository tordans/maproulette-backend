(function(Metrics, $, undefined) {
    Metrics.getChallengeSummaryPieChart = function(canvas, challengeId, showLabels, callback) {
        jsRoutes.org.maproulette.controllers.api.DataController.getChallengeSummary(challengeId).ajax({
            success: function(data) {
                handleChallengeSummaryData(canvas, data, showLabels, callback);
            },
            failure: dataErrorHandler
        });
    };
    
    Metrics.getProjectSummaryPieChart = function(canvas, projects, showLabels, callback) {
        jsRoutes.org.maproulette.controllers.api.DataController.getProjectSummary(projects).ajax({
            success: function(data) {
                handleChallengeSummaryData(canvas, data, showLabels, callback);
            },
            failure: dataErrorHandler
        });
    };

    function handleChallengeSummaryData(canvas, data, showLabels, callback) {
        var numOfChallenges = data.length;
        var totalTasks = 0;
        var fixedTasks = 0;
        var falsePositiveTasks = 0;
        var skippedTasks = 0;
        var alreadyFixedTasks = 0;
        var tooHardTasks = 0;
        var completionRate = 0;
        var falsePositiveRate = 0;
        for (var i = 0; i < numOfChallenges; i++) {
            fixedTasks += data[i].actions.fixed;
            falsePositiveTasks += data[i].actions.falsePositive;
            skippedTasks += data[i].actions.skipped;
            alreadyFixedTasks += data[i].actions.alreadyFixed;
            tooHardTasks += data[i].actions.tooHard;
            totalTasks += data[i].actions.total;
            if (totalTasks > 0) {
                completionRate += (fixedTasks + alreadyFixedTasks) / totalTasks;
            }
            if (totalTasks > 0) {
                falsePositiveRate += falsePositiveTasks / totalTasks;
            }
        }
        updatePieChart(canvas, showLabels,
            totalTasks - fixedTasks - falsePositiveTasks - alreadyFixedTasks - tooHardTasks,
            fixedTasks, falsePositiveTasks, skippedTasks, alreadyFixedTasks, tooHardTasks);
        if (typeof callback !== "undefined") {
            callback({
                numOfChallenges: numOfChallenges,
                totalTasks: totalTasks,
                fixedTasks: fixedTasks,
                falsePositiveTasks: falsePositiveTasks,
                skippedTasks: skippedTasks,
                alreadyFixedTasks: alreadyFixedTasks,
                tooHardTasks: tooHardTasks,
                completionRate: completionRate,
                falsePositiveRate: falsePositiveRate
            });
        }
    }

    function dataErrorHandler(data) {
        ToastUtils.Error("Unable to retrieve data for activity chart.\n" + data);
    }

    function updatePieChart(canvas, showLabels, available, fixed, falsePositive, skipped, alreadyFixed, tooHard) {
        canvas.empty();
        var pieChart = new Chart(canvas, {
            type:"doughnut",
            data: {
                labels: ["Available", "Fixed", "False Positives", "Skipped", "Already Fixed", "Too Hard"],
                datasets: [
                    {
                        data: [available, fixed, falsePositive, skipped, alreadyFixed, tooHard],
                        backgroundColor: ["rgba(0, 0, 0, 1)", "rgba(0, 166, 90, 1)", "rgba(221, 75, 57, 1)", "rgba(243, 156, 18, 1)", "rgba(0, 192, 239, 1)", "rgba(160, 32, 240, 1)"],
                        hoverBackgroundColor: ["rgba(0, 0, 0, 1)", "rgba(0, 166, 90, 1)", "rgba(221, 75, 57, 1)", "rgba(243, 156, 18, 1)", "rgba(0, 192, 239, 1)", "rgba(160, 32, 240, 1)"]
                    }
                ]
            },
            animation:{
                animateScale:true
            },
            options: {
                legend: {
                    display: showLabels
                }
            }
        });
        pieChart.update();
    }
}(window.Metrics = window.Metrics || {}, jQuery));
