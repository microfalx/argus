/**
 * Various utilities for the report.
 */

/**
 * Returns whether the value represents a number
 *
 * @param {Object} value the value to test
 * @return {boolean} true if number, false otherwise
 */
function isNumber(value) {
    return typeof value === 'number' && isFinite(value);
}

/**
 * Returns whether the passed value is defined.
 * @param {Object} value the value to test.
 * @return {Boolean} true if defined, false otherwise
 */
function isDefined(value) {
    return typeof value !== 'undefined';
}

/**
 * Returns whether the passed value is not defined.
 *
 * @param {Object} value the value to test.
 * @return {Boolean} true if not defined, false otherwise
 */
function isUndefined(value) {
    return !isDefined(value);
}

/**
 * Returns whether the passed value is a JavaScript Object.
 *
 * @param {Object} value the value to test.
 * @return {Boolean} true if an object, false otherwise
 */
function isObject(value) {
    return value !== null && value !== undefined && toString.call(value) === '[object Object]' && value.ownerDocument === undefined;
}

/**
 * Copies all the properties of `source` to the specified `target`.
 *
 * @param {Object} target The receiver of the properties.
 * @param {Object} source The source of the properties.
 * @return {Object} returns the target.
 */
function applyProperties(target, source) {
    if (target) {
        if (source && isObject(source)) {
            for (let property in source) {
                if (isObject(target[property])) {
                    applyProperties(target[property], source[property]);
                } else {
                    target[property] = source[property];
                }
            }
        }
    }
    return target || {};
}

/**
 * Copies all the properties of `source` to the specified `target` only if they are undefined.
 *
 * @param {Object} target The receiver of the properties.
 * @param {Object} source The source of the properties.
 * @return {Object} returns the target.
 */
function applyPropertiesIf(target, source) {
    if (target) {
        if (source && isObject(source)) {
            for (let property in source) {
                if (isUndefined(target[property])) target[property] = source[property];
            }
        }
    }
    return target || {};
}

/**
 * Formats a duration in milliseconds into a human-readable string.
 *
 * @param {number} value the value to format
 * @param {boolean} units whether to include units in the output (default: true)
 * @return {string} the formatted duration
 */
function formatMillis(value, units) {
    if (!isDefined(units)) units = true;

    // Pad to 2 or 3 digits, default is 2
    function pad(n, z) {
        z = z || 2;
        return ('00' + n).slice(-z);
    }

    value = Math.abs(value);
    let ms = value % 1000;
    let msp = pad(ms, 3);
    value = (value - ms) / 1000;
    let secs = value % 60;
    let secsp = pad(secs);
    value = (value - secs) / 60;
    let mins = value % 60;
    let minsp = pad(mins);
    let hrs = (value - mins) / 60;
    let displayValue;
    if (units) {
        if (hrs === 0) {
            if (mins === 0) {
                if (secs === 0) {
                    displayValue = ms + "ms";
                } else {
                    displayValue = secs + 's ' + ms + "ms";
                }
            } else {
                displayValue = mins + 'm ' + secs + 's ' + ms + "ms";
            }
        } else {
            displayValue = hrs + 'h ' + mins + 'm ' + secs + 's ' + msp + "ms";
        }
    } else {
        if (hrs === 0) {
            if (mins === 0) {
                displayValue = secsp + '.' + msp;
            } else {
                displayValue = minsp + ':' + secsp + '.' + msp;
            }
        } else {
            displayValue = pad(hrs) + ':' + minsp + ':' + secsp + '.' + msp;
        }
    }
    return displayValue;
}

/**
 * Formats a duration value, which can be either a number (milliseconds) or a string.
 *
 * @param {Object}value the value to format
 * @return {*|string}
 */
function formatDuration(value) {
    if (isNumber(value)) {
        return formatMillis(value);
    } else {
        return value;
    }
}

/**
 * Formats the value in bytes into a human-readable string with appropriate units (KB, MB, GB).
 *
 * @param {number} value the value in bytes
 * @return {string} the formatted value
 */
function formatByte(value) {
    let unit;
    if (value < 10000000) {
        value = value / 1000;
        unit = "KB";
    } else if (value < 10000000000) {
        value = value / 1000000;
        unit = "MB";
    } else {
        value = value / 1000000000;
        unit = "GB";
    }
    return Number(value).toFixed(0) + " " + unit;
}

/**
 * Formats a percentage value with appropriate precision based on its magnitude.
 *
 * @param {number} value the value to format
 * @return {string} the formatted value
 */
function formatPercent(value) {
    let precision = 2;
    if (value > 100 || value < 0.01) {
        precision = 0;
    } else if (value > 5) {
        precision = 1;
    }
    return Number(value).toFixed(precision) + " %";
}

/**
 * Formats a value based on its unit type (COUNT, DURATION, BYTE, PERCENT).
 *
 * @param value the value to format
 * @param unit the unit type of the value
 * @return {*|string} the formatted value
 */
function formatValue(value, unit) {
    if (unit === "COUNT") {
        return value;
    } else if (unit === "DURATION") {
        return formatDuration(value);
    } else if (unit === "BYTE") {
        return formatByte(value);
    } else if (unit === "PERCENT") {
        return formatPercent(value);
    }
}
