(function(Metrics, $, undefined) {
    Metrics.getChallengeSummaryPieChart = function(canvas, challengeId, showLabels, callback, priority, survey) {
        jsRoutes.org.maproulette.controllers.api.DataController.getChallengeSummary(challengeId, (survey) ? 1 : -1, priority).ajax({
            success: function(data) {
                if (survey) {
                    handleSurveySummaryData(canvas, data, showLabels, callback);
                } else {
                    handleChallengeSummaryData(canvas, data, showLabels, callback);
                }
            },
            failure: dataErrorHandler
        });
    };
    
    Metrics.getProjectSummaryPieChart = function(canvas, projects, showLabels, callback, survey) {
        jsRoutes.org.maproulette.controllers.api.DataController.getProjectSummary(projects).ajax({
            success: function(data) {
                if (survey) {
                    handleSurveySummaryData(canvas, data, showLabels, callback);
                } else {
                    handleChallengeSummaryData(canvas, data, showLabels, callback);
                }
            },
            failure: dataErrorHandler
        });
    };

    Metrics.updateChartData = function(flow, chart, sourceData) {
        for (var i = 0; i < sourceData.length; i++) {
            var activityData = chart.data.datasets[i];
            if (typeof activityData !== 'undefined') {
                activityData.data = [];
            }
            var dateKeys = Object.keys(sourceData[i]);
            var currentTotal = 0;
            for (var j = 0; j < dateKeys.length; j++) {
                var dateMoment = moment(dateKeys[j]);
                if (typeof flow === 'undefined' || flow) {
                    currentTotal += sourceData[i][dateKeys[j]];
                    activityData.data[j] = {
                        x: dateMoment.format("ll"),
                        y: currentTotal
                    };
                } else {
                    activityData.data[j] = sourceData[i][dateKeys[j]];
                }
            }
        }
        // update the available separately, as to calculate this it is based off the total tasks
        // available minus the fixed, false positives and already fixed tasks
        chart.update();
    };

    Metrics.getActivityChart = function(type, containerName, data) {
        if (typeof type === 'undefined') {
            type = "line";
        }
        return new Chart($("#" + containerName), {
            type:type,
            data:data,
            options:{
                responsive:true,
                scales:{
                    xAxes: [{
                        type: "time",
                        time: {
                            parser:false,
                            unit:'day',
                            displayFormats: {
                                'day': 'll', // Sep 4 2015
                                'week': 'll', // Week 46, or maybe "[W]WW - YYYY" ?
                                'month': 'MMM YYYY', // Sept 2015
                                'quarter': '[Q]Q - YYYY', // Q3
                                'year': 'YYYY' // 2015
                            },
                            tooltipFormat: ''
                        }
                    }],
                    yAxes: [{
                        scaleLabel: {
                            display: true,
                            labelString: 'count'
                        }
                    }]
                }
            }
        });
    };

    function handleSurveySummaryData(canvas, data, showLabels, callback) {
        var names = new Array(data.length-1);
        var counts = new Array(data.length-1);
        var counter = 0;
        for (var i = 0; i < data.length; i++) {
            if (data[i].id != -3) {
                names[counter] = data[i].name;
                counts[counter] = data[i].count;
                counter++;
            }
        }
        updatePieChart(canvas, showLabels, names, counts);
        if (typeof callback !== "undefined") {
            callback(data);
        }
    }

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
            [
                Messages('metrics.js.status.available'),
                Messages('metrics.js.status.fixed'),
                Messages('metrics.js.status.falsepositive'),
                Messages('metrics.js.status.skipped'),
                Messages('metrics.js.status.alreadyfixed'),
                Messages('metrics.js.status.toohard')
            ],
            [totalTasks - fixedTasks - falsePositiveTasks - alreadyFixedTasks - tooHardTasks,
            fixedTasks, falsePositiveTasks, skippedTasks, alreadyFixedTasks, tooHardTasks]);
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
        ToastUtils.Error(Messages('metrics.js.error') + "\n" + data);
    }

    var backgroundColor = ["rgba(0, 0, 0, 1)", "rgba(0, 166, 90, 1)", "rgba(221, 75, 57, 1)", "rgba(243, 156, 18, 1)", "rgba(0, 192, 239, 1)", "rgba(160, 32, 240, 1)"];
    var hoverBackgroundColor = ["rgba(0, 0, 0, 1)", "rgba(0, 166, 90, 1)", "rgba(221, 75, 57, 1)", "rgba(243, 156, 18, 1)", "rgba(0, 192, 239, 1)", "rgba(160, 32, 240, 1)"];

    function updatePieChart(canvas, showLabels, labels, results) {
        canvas.empty();

        var pieChart = new Chart(canvas, {
            type:"doughnut",
            data: {
                labels: labels,
                datasets: [
                    {
                        data: results,
                        backgroundColor: backgroundColor.slice(0, labels.length),
                        hoverBackgroundColor: hoverBackgroundColor.slice(0, labels.length)
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
