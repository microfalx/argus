/**
 * Renders a pie chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 * @see #renderChart
 */
function renderPieChart(options) {
    options = applyProperties({
        title: {
            align: 'left',
            margin: 10
        },
        legend: {},
        noData: {
            text: "No data available",
            align: 'center',
            verticalAlign: 'middle',
            style: {
                fontSize: '14px',
            },
        },
        chart: {
            type: 'pie',
            animations: {
                enabled: false
            },
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false,
            }
        }
    }, options);
    renderChart(options);
}

/**
 * Renders a bar chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 * @see #renderChart
 */
function renderBarChart(options) {
    options = applyProperties({
        chart: {
            type: 'bar',
            animations: {
                enabled: false
            },
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false,
            }
        },
        title: {
            align: 'left',
            margin: 10
        },
        plotOptions: {
            bar: {
                borderRadius: 4,
                borderRadiusApplication: 'end',
                horizontal: true,
            }
        },
        dataLabels: {
            enabled: false
        }
    }, options);
    renderChart(options);
}

/**
 * Renders a column (horizontal bars) chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 * @see #renderChart
 */
function renderColumnChart(options) {
    options = applyProperties({
        chart: {
            type: 'bar',
            animations: {
                enabled: false
            },
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false,
            }
        },
        plotOptions: {
            bar: {
                horizontal: false,
                columnWidth: '55%',
                borderRadius: 5,
                borderRadiusApplication: 'end'
            },
        },
        dataLabels: {
            enabled: false
        },
        stroke: {
            show: true,
            width: 2,
            colors: ['transparent']
        },
        fill: {
            opacity: 1
        },
        title: {
            align: 'left',
            margin: 10
        },
        tooltip: {
            y: {
                formatter: function (val) {
                    return val
                }
            }
        }
    }, options);
    renderChart(options);
}

/**
 * Renders a bar chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 * @see #renderChart
 */
function renderAreaChart(options) {
    const unit = options.unit;
    delete options.unit;
    options = applyProperties({
        chart: {
            type: 'area',
            animations: {
                enabled: false
            },
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false,
            }
        },
        dataLabels: {
            enabled: false
        },
        stroke: {
            curve: 'monotoneCubic'
        },
        fill: {
            type: 'gradient',
            gradient: {
                opacityFrom: 0.6,
                opacityTo: 0.8,
            }
        },
        title: {
            align: 'left',
            margin: 10
        },
        legend: {
            position: 'top',
            horizontalAlign: 'left'
        },
        yaxis: {
            labels: {
                formatter: function (value) {
                    return formatValue(value, unit)
                }
            }
        },
        tooltip: {
            enabled: true,
            followCursor: true,
            onDatasetHover: {
                highlightDataSeries: true,
            },
            y: {
                formatter: function (value) {
                    return formatValue(value, unit)
                },
                title: {
                    formatter: (seriesName) => seriesName,
                },
            }
        },
    }, options);
    renderChart(options);
}

/**
 * Renders a bar chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 * @see #renderChart
 */
function renderTreeMapChart(options) {
    const unit = options.unit;
    delete options.unit;
    options = applyProperties({
        legend: {
            show: false
        },
        title: {
            align: 'left',
            margin: 10
        },
        noData: {
            text: "No data available",
            align: 'center',
            verticalAlign: 'middle',
            style: {
                fontSize: '14px',
            },
        },
        tooltip: {
            enabled: true,
            followCursor: true,
            onDatasetHover: {
                highlightDataSeries: true,
            },
            y: {
                formatter: function (value) {
                    return formatValue(value, unit)
                },
                title: {
                    formatter: (seriesName) => seriesName,
                },
            }
        },
        chart: {
            type: 'treemap',
            animations: {
                enabled: false
            },
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false,
            }
        }
    }, options);
    renderChart(options);
}

/**
 * Renders a chart using ApexCharts for the given element ID.
 *
 * The DOM identifier should be provided in the options object as the `id` property.
 *
 * @param {Object} options options to configure the chart.
 */
function renderChart(options) {
    let queue = window._chartQueue = window._chartQueue || [];
    queue.push(options);
    if (!window._chartProcessing) {
        window._chartProcessing = true;
        $(document).ready(function () {
            console.info("Initialize charts");
            renderNextChar();
            // just in case, fire a delayed processing
            setTimeout(renderNextChar, 30000);
        });
    }
}

/**
 * Picks a chart from the queue and renders it.
 *
 * This function is called recursively until all charts in the queue are rendered.
 */
function renderNextChar() {
    while (window._chartQueue.length > 0) {
        let options = window._chartQueue.shift();
        if (options) doRenderChart(options);
    }
}

/**
 * Actually renders the chart using ApexCharts for the given element ID.
 *
 * @param {Object} options options to configure the chart.
 */
function doRenderChart(options) {
    const id = options.id;
    delete options.id;
    let dom = document.querySelector("#" + id);
    if (dom) {
        //console.info("Initialize chart :", dom);
        let chart = new ApexCharts(dom, options);
        setTimeout(function () {
            chart.render();
        }, 0);
    }
    renderNextChar();
}