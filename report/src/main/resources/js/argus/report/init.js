/**
 * Scroll to the top button
 */
function initMoveToTopButton() {
    console.info("Initialize move to top button");
    let backToTopButton = document.getElementById("btn-back-to-top");
    if (backToTopButton) {
        backToTopButton.addEventListener("click", function () {
            document.body.scrollTop = 0;
            document.documentElement.scrollTop = 0;
        });
    }
    window.onscroll = function () {
        scrollFunction();
    };
    function scrollFunction() {
        if (document.body.scrollTop > 20 || document.documentElement.scrollTop > 20) {
            backToTopButton.style.display = "block";
        } else {
            backToTopButton.style.display = "none";
        }
    }
}

/**
 * Initializes the tooltips for the report.
 */
function initializeTooltips() {
    console.info("Initialize tooltips");
    const tooltipElements = $('[data-toggle="tooltip"]').toArray();

    function processNextTooltip() {
        const element = tooltipElements.shift();
        if (!element) {
            console.info("Tooltips initialized");
            return;
        }
        //console.info("Initialize tooltip: ", element);
        try {
            $(element).tooltip();
        } catch (e) {
            console.error("Failed to process tooltip for element: ", element, e);
        }
        window.setTimeout(processNextTooltip, 0);
    }

    window.setTimeout(processNextTooltip, 0);
}

/**
 * Returns the sparkline options for the given type.
 *
 * @param {String} type the type of sparkline ('score' or 'memory')
 * @return {Object} sparkline options
 */
function getSparklineOptions(type) {
    if (type === 'score') {
        return {
            type: 'bar',
            chartRangeMin: 1,
            chartRangeMax: 10,
            barColor: '#4099ff', // Default color for unmapped values
            colorMap: {
                '1:4': '#d63939',
                '4:7': '#f59f00',
                '7:10': '#2fb344'
            },
            zeroAxis: true
        };
    } else if (type === 'memory') {
        return {
            type: 'bar',
            barColor: '#4099ff', // Default color for unmapped values
            colorMap: {
                '50000000:1000000000000': '#d63939',
                '30000000:50000000': '#d55858',
                '20000000:30000000': '#ef8888',
                '10000000:20000000': '#f59f00',
                '5000000:10000000': '#ffb931',
                '0:5000000': '#2fb344',
                '0:1000000': '#76c885'
            },
            tooltipSuffix: ' bytes',
            zeroAxis: true
        };
    }
}

/**
 * Initializes the sparklines for the report.
 */
function initializeSparklines() {
    console.info("Initialize sparklines");
    const sparklineBarElements = $('.sparkline-bar');
    const sparklineBars = sparklineBarElements.toArray();

    function processSparkline(element) {
        const isScore = $(element).hasClass('sparkline-score');
        const isMemory = $(element).hasClass('sparkline-memory');
        if (!(isScore || isMemory)) return;
        const options = getSparklineOptions(isScore ? 'score' : 'memory');
        //console.info("Initialize sparkline: ", element);
        $(element).removeClass('d-none');
        $(element).sparkline('html', options);
    }

    function processNextSparkline() {
        const element = sparklineBars.shift();
        if (!element) {
            console.info("Sparklines initialized");
            return;
        }
        processSparkline(element);
        window.setTimeout(processNextSparkline, 0);
    }

    window.setTimeout(processNextSparkline, 0);
}

function initializeDataTables() {
    console.info("Initialize data tables");
    const tables = $('.datatable').toArray();
    const delayMs = 50;

    function processNextTable() {
        const table = tables.shift();
        if (!table) {
            console.info("DataTables initialized");
            initializeTooltips();
            return;
        }
        //console.info("Initialize data table: ", table);
        if (!$.fn.DataTable.isDataTable(table)) {
            $(table).DataTable({
                "order": [],
                scrollX: true,
                autoWidth: false
            });
        }
        window.setTimeout(processNextTable, delayMs);
    }

    window.setTimeout(processNextTable, 0);
}

/**
 * Initializes the report by setting up the move to top button, sparklines, and data tables.
 */
function initReport() {
    console.log("Initializing report...");
    $(document).ready(function () {
        initMoveToTopButton();
        initializeSparklines();
        initializeDataTables();
    });
}