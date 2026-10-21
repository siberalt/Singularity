google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(start);

// Зоны режима: всё посчитано заранее, страница только рисует.
let model = null;
let sideAt = null;
let chart = null;
let angleChart = null;

const UP_COLOUR = '#188038';
const FLAT_COLOUR = '#f9ab00';
const DOWN_COLOUR = '#d93025';
const UNMARKED_COLOUR = '#9aa0a6';
const AVERAGE_COLOUR = '#1a73e8';
const ANGLE_COLOUR = '#5e35b1';

function start() {
    fetch('TrendZones.json')
        .then(response => response.ok ? response.json() : Promise.reject(response.status))
        .then(zones => draw(zones))
        .catch(error => {
            document.getElementById('caption').textContent =
                'Не удалось прочитать TrendZones.json: запустите TrendZoneSimulation, она пишет этот файл.';
            console.error('Error loading zones:', error);
        });
}

function draw(zones) {
    model = zones;
    sideAt = sidesOf(model['zones'], model['price'].length);
    chart = new google.visualization.ComboChart(document.getElementById('price_chart'));
    angleChart = new google.visualization.ComboChart(document.getElementById('angle_chart'));

    writeLegend();
    document.getElementById('average').addEventListener('change', drawAll);
    document.getElementById('angleChart').addEventListener('change', drawAll);
    window.addEventListener('resize', drawAll);

    drawAll();
}

/**
 * Сторона каждого бара по зонам, или undefined до первой зоны.
 *
 * Разворачивается в массив, а не ищется по зонам на каждый бар: зон сотни, баров тысячи, и поиск в
 * отрисовке дал бы произведение там, где хватает одного прохода.
 */
function sidesOf(zones, bars) {
    const sides = new Array(bars);

    for (const zone of zones) {
        for (let row = zone['from']; row <= zone['to']; row++) {
            sides[row] = zone['side'];
        }
    }

    return sides;
}

function writeLegend() {
    const settings = model['settings'];
    const shares = sharesOf();

    document.getElementById('legend').innerHTML =
        item(UP_COLOUR, 'вверх ' + shares.up + '%')
        + item(FLAT_COLOUR, 'флэт ' + shares.flat + '%')
        + item(DOWN_COLOUR, 'вниз ' + shares.down + '%')
        + item(UNMARKED_COLOUR, 'прогрев ' + shares.unmarked + '%')
        + item(AVERAGE_COLOUR, 'SMA(' + settings['sma'] + ')')
        + item(ANGLE_COLOUR, byCrossings()
            ? 'пересечений цены со средней в окрестности ' + settings['window'] + ' баров'
            : 'угол средней по ' + settings['bars'] + ' приращениям')
        + '<span>· ' + (byCrossings()
            ? 'флэт от ' + settings['crowd'] + ' пересечений в окрестности'
            : 'объявление ' + settings['decisive'] + ', удержание ' + settings['remains']
              + ' — долей предельных 45°') + '</span>';

    document.getElementById('caption').textContent =
        'бумага ' + model['instrument'] + ' · ' + model['price'].length + ' баров ' + model['interval']
        + ' · зон ' + model['zones'].length
        + ' · ' + shortDate(model['times'][0]) + ' → ' + shortDate(model['times'][model['times'].length - 1]);
}

function sharesOf() {
    const bars = model['price'].length;
    let up = 0;
    let flat = 0;
    let down = 0;

    for (const side of sideAt) {
        if (side > 0) {
            up++;
        } else if (side < 0) {
            down++;
        } else if (side === 0) {
            flat++;
        }
    }

    const percent = count => Math.round(100 * count / bars);

    return {
        up: percent(up), flat: percent(flat), down: percent(down),
        unmarked: percent(bars - up - flat - down)
    };
}

function item(colour, text) {
    return '<span><i class="swatch" style="background:' + colour + '"></i>' + text + '</span>';
}

function drawAll() {
    drawPrice();
    drawAngle();
}

/**
 * Цена одна, но покрашена режимом: по серии на сторону, в остальных строках null.
 *
 * Серия берёт и бар перед своей зоной тоже - иначе на каждой границе оставался бы разрыв в один бар,
 * и полоса выглядела бы пунктиром из-за склейки, а не из-за режима.
 */
function drawPrice() {
    const table = new google.visualization.DataTable();
    const columns = [];
    const series = {};
    const painted = side => row =>
        sideAt[row] === side || sideAt[row + 1] === side ? model['price'][row] : null;

    table.addColumn('string', 'Время');
    addColumn(table, columns, series, 'Восходящий', painted(1), {color: UP_COLOUR, lineWidth: 2});
    addColumn(table, columns, series, 'Флэт', painted(0), {color: FLAT_COLOUR, lineWidth: 2});
    addColumn(table, columns, series, 'Нисходящий', painted(-1), {color: DOWN_COLOUR, lineWidth: 2});
    // До первой зоны мера молчит - бары всё равно рисуются, но серым: это не флэт, это нет ответа.
    addColumn(table, columns, series, 'Не размечено',
        row => sideAt[row] === undefined || sideAt[row + 1] === undefined ? model['price'][row] : null,
        {color: UNMARKED_COLOUR, lineWidth: 1});

    if (document.getElementById('average').checked) {
        addColumn(table, columns, series, 'SMA(' + model['settings']['sma'] + ')',
            row => model['sma'][row], {color: AVERAGE_COLOUR, lineWidth: 1});
    }

    table.addRows(rowsOf(columns));
    chart.draw(table, options(series, priceWindow()));
}

/**
 * Шкала цены по самой цене, а не от нуля.
 *
 * Нуля в этом ряду нет и быть не может, а пущенная от него шкала отдаёт движению цены нижнюю треть
 * холста: на бумаге, ходившей между 100 и 380, две трети высоты занимает пустота под минимумом.
 * Поля в два процента размаха - чтобы крайние бары не упирались в рамку.
 */
function priceWindow() {
    let min = Infinity;
    let max = -Infinity;

    // Средняя сюда входит наравне с ценой: на развороте она выходит за крайний бар окна, и шкала,
    // посчитанная по одной цене, срезала бы ей вершину.
    for (const value of model['price'].concat(model['sma'])) {
        if (value === null) {
            continue;
        }

        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    const margin = (max - min) * 0.02;

    return {viewWindow: {min: min - margin, max: max + margin}};
}

// Режим размечен частотой пересечений, а не углом: от этого зависит и подпись, и нижний график.
function byCrossings() {
    return model['settings']['byCrossings'] === true && Array.isArray(model['crossings']);
}

/**
 * Под ценой - то, из чего получились зоны, а не что-нибудь похожее: угол с его порогами или счёт
 * пересечений со своим. Рисовать угол под разметкой по пересечениям значило бы показывать одну меру,
 * а размечать другой.
 */
function drawAngle() {
    const holder = document.getElementById('angle_chart');
    holder.hidden = !document.getElementById('angleChart').checked;

    if (holder.hidden) {
        return;
    }

    const table = new google.visualization.DataTable();
    const columns = [];
    const series = {};

    table.addColumn('string', 'Время');

    if (byCrossings()) {
        const crowd = model['settings']['crowd'];
        const highest = model['crossings'].reduce((top, one) => Math.max(top, one === null ? 0 : one), 0);

        // Ступеньками, а не линией: счёт - целое число, и наклонные между соседними барами рисовали бы
        // переход, которого нет.
        addColumn(table, columns, series, 'Пересечений в окрестности', row => model['crossings'][row],
            {color: ANGLE_COLOUR, lineWidth: 1});
        threshold(table, columns, series, 'гроздь', crowd, FLAT_COLOUR);

        table.addRows(rowsOf(columns));
        angleChart.draw(table, options(series, {
            viewWindow: {min: 0, max: Math.max(crowd + 1, highest)},
            ticks: ticksTo(Math.max(crowd + 1, highest))
        }));

        return;
    }

    const decisive = model['settings']['decisive'];
    const remains = model['settings']['remains'];

    addColumn(table, columns, series, 'Угол средней', row => model['angle'][row],
        {color: ANGLE_COLOUR, lineWidth: 1});
    threshold(table, columns, series, 'объявление вверх', decisive, UP_COLOUR);
    threshold(table, columns, series, 'удержание вверх', remains, UP_COLOUR);
    threshold(table, columns, series, 'удержание вниз', -remains, DOWN_COLOUR);
    threshold(table, columns, series, 'объявление вниз', -decisive, DOWN_COLOUR);

    table.addRows(rowsOf(columns));
    angleChart.draw(table, options(series, {
        viewWindow: {min: -1, max: 1},
        ticks: [-1, -decisive, -remains, 0, remains, decisive, 1]
    }));
}

function ticksTo(highest) {
    const ticks = [];

    for (let at = 0; at <= highest; at++) {
        ticks.push(at);
    }

    return ticks;
}

function threshold(table, columns, series, title, level, colour) {
    addColumn(table, columns, series, title, () => level,
        {color: colour, lineWidth: 1, lineDashStyle: [4, 4]});
}

function rowsOf(columns) {
    const rows = [];

    for (let row = 0; row < model['price'].length; row++) {
        rows.push([shortDate(model['times'][row])].concat(columns.map(value => value(row))));
    }

    return rows;
}

function options(series, vAxis) {
    return {
        chartArea: {width: '93%', height: '84%', top: 14, left: 70, right: 30},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: series,
        hAxis: {showTextEvery: Math.ceil(model['price'].length / 12)},
        vAxis: vAxis
    };
}

// Номер серии - это номер столбца данных: интервалов здесь нет, так что они совпадают.
function addColumn(table, columns, series, title, value, style) {
    series[Object.keys(series).length] = style;
    columns.push(value);
    table.addColumn('number', title);
}

function shortDate(time) {
    return new Date(time).toLocaleDateString('ru-RU', {day: '2-digit', month: '2-digit', year: '2-digit'});
}
