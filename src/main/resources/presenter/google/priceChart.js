google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(start);

// Один прогон симуляции: цена с заявками, RSI под ней и объём. Всё считается один раз, смена
// масштаба только перерисовывает.
let priceData = null;
let volumeData = null;
let labels = null;
let rsi = null;
let stepMinutes = 0;
let span = '';
let resizing = null;
let priceRange = null;

const RSI_PERIOD = 14;
const RSI_COLOUR = '#6a1b9a';
// Те же уровни, что на странице кадров: перепроданность, на которой держится переживший сигнал.
const RSI_OVERSOLD = 20;
const RSI_OVERBOUGHT = 70;
// Поля области графика в пикселях, одни на все три: иначе одна и та же минута окажется на разных
// графиках в разных местах.
// Предел ширины одной картинки в браузере - около 32 767 пикселей, и у самой границы отрисовка молча
// срывается: график остаётся пустым. Запас в пару тысяч дешевле, чем пустая страница.
const MAX_WIDTH = 30000;
// Мелкий шрифт на осях: делений много, а числа на них короткие.
const AXIS_TEXT = {fontSize: 11};
const LEFT = 80;
const RIGHT = 30;

function start() {
    Promise.all([load('PriceChart.json'), load('VolumeChart.json')])
        .then(([price, volume]) => {
            priceData = price;
            volumeData = volume;
            prepare();
            document.getElementById('zoom').addEventListener('change', render);
            // Перерисовка трёх картинок в тридцать тысяч пикселей занимает секунды, а событий изменения
            // размера приходит десятки подряд - ждём, пока тянуть окно перестанут.
            window.addEventListener('resize', () => {
                clearTimeout(resizing);
                resizing = setTimeout(render, 300);
            });
            render();
        })
        .catch(() => {
        });
}

function load(file) {
    return fetch(file)
        .then(response => response.ok ? response.json() : Promise.reject(response.status))
        .catch(error => {
            report(file, error);

            return Promise.reject(error);
        });
}

// Пустая страница ничего не говорит о том, чего не хватает, поэтому говорим прямо.
function report(file, error) {
    const missing = document.getElementById('missing');

    missing.hidden = false;
    missing.textContent += 'Не удалось прочитать ' + file + ': запустите симуляцию, она пишет этот файл. ';
    console.error('Error loading JSON data:', error);
}

function prepare() {
    const times = priceData['data'].map(row => row[0]);

    labels = times.map(formatDate);
    stepMinutes = times.length > 1 ? Math.round((new Date(times[1]) - new Date(times[0])) / 60000) : 0;
    const prices = priceData['data'].map(row => row[1]).filter(price => price !== null && price !== undefined);
    const low = Math.min(...prices);
    const high = Math.max(...prices);
    // Ось от нуля прижимает цену к верхней трети поля: у бумаги за 300 рублей колебания в десять
    // рублей на такой шкале не видно вовсе.
    const margin = (high - low) * 0.05;

    priceRange = {min: low - margin, max: high + margin};
    rsi = rsiOf(priceData['data'].map(row => row[1]), RSI_PERIOD);

    span = times.length + ' точек по ' + stepMinutes + ' мин · ' + formatDate(times[0]) + ' → '
        + formatDate(times[times.length - 1]);
}

/**
 * RSI Уайлдера по точкам самого графика. Пропуски в цене переносятся вперёд: точка без сделки - это
 * отсутствие движения, а не движение к нулю.
 */
function rsiOf(prices, period) {
    const values = new Array(prices.length).fill(null);
    let before = null;
    let gain = 0;
    let loss = 0;
    let counted = 0;

    for (let at = 0; at < prices.length; at++) {
        const price = prices[at] === null || prices[at] === undefined ? before : prices[at];

        if (price === null) {
            continue;
        }

        if (before !== null) {
            const change = price - before;
            const up = Math.max(change, 0);
            const down = Math.max(-change, 0);

            counted++;

            if (counted <= period) {
                gain += up / period;
                loss += down / period;
            } else {
                gain += (up - gain) / period;
                loss += (down - loss) / period;
            }

            if (counted >= period) {
                values[at] = loss === 0 ? 100 : 100 - 100 / (1 + gain / loss);
            }
        }

        before = price;
    }

    return values;
}

function render() {
    const scroller = document.getElementById('scroller');
    // Масштаб задаётся в ширинах окна, а не в пикселях на точку: сколько точек в прогоне - заранее
    // неизвестно, а ширина экрана известна всегда, и «по окну» открывает страницу такой же, какой она
    // была до прокрутки.
    const wanted = scroller.clientWidth * Number(document.getElementById('zoom').value);
    const width = Math.max(scroller.clientWidth, Math.min(wanted, MAX_WIDTH));
    // Одна подпись примерно на полтораста пикселей - иначе они наезжают друг на друга.
    const everyLabel = Math.max(1, Math.ceil(labels.length / Math.max(1, (width - LEFT - RIGHT) / 150)));

    drawChart('price_chart', priceData, width, everyLabel, {vAxis: {textStyle: AXIS_TEXT, viewWindow: priceRange}});
    drawRsi(width, everyLabel);
    // Объём измеряется миллионами штук, и полностью выписанное число съедает поле оси у всех трёх.
    drawChart('volume_chart', volumeData, width, everyLabel, {
        vAxis: {textStyle: AXIS_TEXT, format: 'short'},
        series: volumeSeries(width)
    });
    document.getElementById('span').textContent = span + ' · '
        + scaleOf((width - LEFT - RIGHT) / labels.length)
        + (wanted > MAX_WIDTH ? ' (упёрлось в предел ширины)' : '');
}

/** Ниже пикселя на точку считать пиксели незачем - понятнее, сколько точек в него сложено. */
function scaleOf(perPoint) {
    return perPoint >= 1 ? perPoint.toFixed(1) + ' пикс. на точку'
        : Math.round(1 / perPoint) + ' точек на пиксель';
}

/**
 * Столбиками или заливкой. Уже двух пикселей Google столбик не делает и вместо сжатия раздвигает всю
 * область: на шестидесяти тысячах баров ось объёма уезжала от цены в восемнадцать раз. На такой
 * плотности столбик и заливка неразличимы, а заливка ширину области слушается.
 */
function volumeSeries(width) {
    const own = volumeData['options']['series']['0'];

    return labels.length * 2 > width - LEFT - RIGHT ? {0: {...own, type: 'area'}} : {0: own};
}

function drawChart(containerId, jsonData, width, everyLabel, extra) {
    const table = new google.visualization.DataTable();

    jsonData['columns'].forEach(column => {
        table.addColumn(column.type === 'date' ? {...column, type: 'string'} : column);
    });
    table.addRows(jsonData['data'].map(row => row.map((value, at) =>
        jsonData['columns'][at].type === 'date' ? formatDate(value) : value)));

    const container = document.getElementById(containerId);

    container.style.width = width + 'px';
    new google.visualization.ComboChart(container).draw(table, {
        // Ширина области задаётся явно: от left и right Google считает её сам и для разных наборов данных
        // считает по-разному - одна и та же минута уезжала у объёма на тысячу пикселей в сторону.
        chartArea: {left: LEFT, width: width - LEFT - RIGHT, top: 16, bottom: 40},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: jsonData['options']['series'],
        hAxis: {showTextEvery: everyLabel, textStyle: AXIS_TEXT},
        ...extra
    });
}

function drawRsi(width, everyLabel) {
    const table = new google.visualization.DataTable();

    table.addColumn('string', 'Время');
    table.addColumn('number', 'RSI(' + RSI_PERIOD + ') по ' + stepMinutes + ' мин');
    table.addColumn('number', 'перепроданность');
    table.addColumn('number', 'перекупленность');
    table.addRows(labels.map((label, at) => [label, rsi[at], RSI_OVERSOLD, RSI_OVERBOUGHT]));

    const container = document.getElementById('rsi_chart');

    container.style.width = width + 'px';
    new google.visualization.ComboChart(container).draw(table, {
        chartArea: {left: LEFT, width: width - LEFT - RIGHT, top: 10, bottom: 30},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: {
            0: {color: RSI_COLOUR, lineWidth: 1},
            1: {color: '#188038', lineWidth: 1, lineDashStyle: [4, 4]},
            2: {color: '#d93025', lineWidth: 1, lineDashStyle: [4, 4]}
        },
        hAxis: {showTextEvery: everyLabel, textStyle: AXIS_TEXT},
        vAxis: {
            viewWindow: {min: 0, max: 100},
            ticks: [0, RSI_OVERSOLD, 50, RSI_OVERBOUGHT, 100],
            gridlines: {count: -1},
            textStyle: AXIS_TEXT
        }
    });
}

function formatDate(time) {
    return new Date(time).toLocaleString('ru-RU', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}
