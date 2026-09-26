google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(start);

// Кадры уровней: всё посчитано заранее, шаг только перерисовывает.
let model = null;
let frameAt = 0;
let lowAt = null;
let scale = null;
let chart = null;
let rsiChart = null;

const ZONE_COLOUR = '#8e24aa';
const RSI_COLOUR = '#6a1b9a';
// Перепроданность, на которой держится единственный переживший проверки сигнал, и перекупленность.
const RSI_OVERSOLD = 20;
const RSI_OVERBOUGHT = 70;

function start() {
    fetch('LevelFrames.json')
        .then(response => response.ok ? response.json() : Promise.reject(response.status))
        .then(frames => startStepper(frames))
        .catch(error => {
            document.getElementById('caption').textContent =
                'Не удалось прочитать LevelFrames.json: запустите LevelDetectorSimulation, она пишет этот файл.';
            console.error('Error loading frames:', error);
        });
}

function startStepper(frames) {
    model = frames;
    lowAt = new Map(model['lows']);
    scale = {min: Math.min(...model['price']), max: Math.max(...model['price'])};
    chart = new google.visualization.ComboChart(document.getElementById('price_chart'));

    if (model['rsi']) {
        document.getElementById('rsi_chart').hidden = false;
        rsiChart = new google.visualization.ComboChart(document.getElementById('rsi_chart'));
    }

    document.getElementById('legend').innerHTML =
        item('#1a73e8', 'цена в окне') + item('#9aa0a6', 'цена после окна')
        + item('#33691e', '3 касания') + item('#e65100', '4–5') + item('#b71c1c', '6 и больше')
        + item(ZONE_COLOUR, 'зона') + item('#188038', 'минимум впереди у линии') + item('#d93025', 'минимум мимо')
        + item(RSI_COLOUR, 'RSI(14) снизу')
        + '<span>· окно ' + model['window'] + ' баров, шаг ' + model['step']
        + ', вперёд ' + model['lookahead'] + '; у линии — ближе ' + model['catchTolerance'] + ' ATR</span>';

    const slider = document.getElementById('slider');
    slider.max = model['frames'].length - 1;
    slider.addEventListener('input', () => {
        frameAt = Number(slider.value);
        drawFrame();
    });
    document.getElementById('back').addEventListener('click', () => step(-1));
    document.getElementById('forward').addEventListener('click', () => step(1));
    document.getElementById('projection').addEventListener('change', drawFrame);
    document.getElementById('fixedScale').addEventListener('change', drawFrame);
    document.addEventListener('keydown', onKey);
    window.addEventListener('resize', drawFrame);

    drawFrame();
}

function item(colour, text) {
    return '<span><i class="swatch" style="background:' + colour + '"></i>' + text + '</span>';
}

function onKey(event) {
    const by = event.shiftKey ? 10 : 1;

    if (event.key === 'ArrowLeft') {
        step(-by);
    } else if (event.key === 'ArrowRight') {
        step(by);
    } else if (event.key === 'Home') {
        step(-model['frames'].length);
    } else if (event.key === 'End') {
        step(model['frames'].length);
    } else {
        return;
    }

    event.preventDefault();
}

function step(by) {
    const moved = Math.min(Math.max(frameAt + by, 0), model['frames'].length - 1);

    if (moved !== frameAt) {
        frameAt = moved;
        document.getElementById('slider').value = frameAt;
        drawFrame();
    }
}

// Цена уровня в строке: линия прямая по индексу свечи, а не по номеру бара - ночи и выходные
// оставляют в индексах дыры, и между концами отрезка её интерполировать нельзя.
function levelPrice(level, row) {
    return level['price'] + level['slope'] * (model['index'][row] - model['index'][level['from']]);
}

function drawFrame() {
    const frame = model['frames'][frameAt];
    const end = Math.min(frame['to'] + model['lookahead'], model['price'].length);
    const table = new google.visualization.DataTable();
    const columns = [];
    const series = {};

    table.addColumn('string', 'Время');
    addColumn(table, columns, series, 'Цена',
        row => row <= frame['to'] ? model['price'][row] : null, {color: '#1a73e8', lineWidth: 1});
    // Цена после окна - другим цветом: так видно границу, за которой уровни уже ничего не видели.
    addColumn(table, columns, series, 'Цена после окна',
        row => row >= frame['to'] ? model['price'][row] : null, {color: '#9aa0a6', lineWidth: 1});

    frame['levels'].forEach((level, number) => {
        const colour = level['touches'] >= 6 ? '#b71c1c' : level['touches'] >= 4 ? '#e65100' : '#33691e';

        addColumn(table, columns, series, 'Уровень ' + (number + 1) + ': ' + level['touches'] + ' касаний',
            row => row >= level['from'] && row <= level['to'] ? levelPrice(level, row) : null,
            {color: colour, lineWidth: 2});

        if (document.getElementById('projection').checked) {
            addColumn(table, columns, series, 'Уровень ' + (number + 1) + ' вперёд',
                row => row >= level['to'] ? levelPrice(level, row) : null,
                {color: colour, lineWidth: 1, lineDashStyle: [4, 4]});
        }
    });

    // Зона - полоса между интервалами своей серии: сложенные области сложили бы зоны друг на друга.
    const zones = frame['zones'] || [];
    const zoneEnd = document.getElementById('projection').checked ? end - 1 : frame['to'] - 1;

    zones.forEach((zone, number) => {
        const inside = row => row >= zone['from'] && row <= zoneEnd;

        addColumn(table, columns, series,
            'Зона ' + (number + 1) + ': ' + zone['touches'] + ' касаний, вес ' + zone['strength'].toFixed(1),
            row => inside(row) ? (zone['low'] + zone['high']) / 2 : null,
            {color: ZONE_COLOUR, lineWidth: 1, lineDashStyle: [2, 4]});
        addInterval(table, columns, row => inside(row) ? zone['low'] : null);
        addInterval(table, columns, row => inside(row) ? zone['high'] : null);
    });

    const inZone = row => zones.some(zone => zone['low'] <= lowAt.get(row) && lowAt.get(row) <= zone['high']);
    const caught = row => frame['levels'].some(
        level => Math.abs(lowAt.get(row) - levelPrice(level, row)) <= model['catchTolerance'] * frame['volatility']);

    addColumn(table, columns, series, 'Минимум в окне',
        row => row < frame['to'] && lowAt.has(row) ? lowAt.get(row) : null,
        {color: '#33691e', pointSize: 6, pointShape: 'circle', lineWidth: 0});
    addColumn(table, columns, series, 'Минимум у линии',
        row => row >= frame['to'] && lowAt.has(row) && caught(row) ? lowAt.get(row) : null,
        {color: '#188038', pointSize: 11, pointShape: 'triangle', lineWidth: 0});
    addColumn(table, columns, series, 'Минимум мимо',
        row => row >= frame['to'] && lowAt.has(row) && !caught(row) ? lowAt.get(row) : null,
        {color: '#d93025', pointSize: 8, pointShape: 'square', lineWidth: 0});

    const rows = [];

    for (let row = frame['from']; row < end; row++) {
        rows.push([shortTime(model['times'][row])].concat(columns.map(value => value(row))));
    }

    table.addRows(rows);
    drawRsi(frame, end);
    chart.draw(table, {
        chartArea: {width: '93%', height: '86%', top: 16, left: 70, right: 30},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: series,
        intervals: {style: 'area', fillOpacity: 0.18, lineWidth: 0},
        hAxis: {showTextEvery: Math.ceil(rows.length / 12)},
        vAxis: document.getElementById('fixedScale').checked
            ? {viewWindow: {min: scale.min, max: scale.max}}
            : {}
    });
    writeCaption(frame, end, caught, inZone);
}

// RSI тех же строк, что и цена сверху: левое поле и ширина области совпадают с графиком цены, иначе
// один и тот же бар оказывался бы на двух графиках в разных местах.
function drawRsi(frame, end) {
    if (rsiChart === null) {
        return;
    }

    const table = new google.visualization.DataTable();

    table.addColumn('string', 'Время');
    table.addColumn('number', 'RSI(14) в окне');
    table.addColumn('number', 'RSI(14) после окна');
    table.addColumn('number', 'перепроданность');
    table.addColumn('number', 'перекупленность');

    const rows = [];

    for (let row = frame['from']; row < end; row++) {
        rows.push([
            shortTime(model['times'][row]),
            row <= frame['to'] ? model['rsi'][row] : null,
            row >= frame['to'] ? model['rsi'][row] : null,
            RSI_OVERSOLD,
            RSI_OVERBOUGHT
        ]);
    }

    table.addRows(rows);
    rsiChart.draw(table, {
        chartArea: {width: '93%', height: '78%', top: 10, left: 70, right: 30},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: {
            0: {color: RSI_COLOUR, lineWidth: 1},
            1: {color: '#b39ddb', lineWidth: 1},
            2: {color: '#188038', lineWidth: 1, lineDashStyle: [4, 4]},
            3: {color: '#d93025', lineWidth: 1, lineDashStyle: [4, 4]}
        },
        hAxis: {showTextEvery: Math.ceil(rows.length / 12)},
        vAxis: {viewWindow: {min: 0, max: 100}, ticks: [0, RSI_OVERSOLD, 50, RSI_OVERBOUGHT, 100]}
    });
}

// Номер серии - это номер столбца данных без интервалов: интервал принадлежит серии перед ним.
function addColumn(table, columns, series, title, value, style) {
    series[Object.keys(series).length] = style;
    columns.push(value);
    table.addColumn('number', title);
}

function addInterval(table, columns, value) {
    columns.push(value);
    table.addColumn({type: 'number', role: 'interval'});
}

function writeCaption(frame, end, caught, inZone) {
    let hit = 0;
    let missed = 0;
    let zoned = 0;

    for (let row = frame['to']; row < end; row++) {
        if (lowAt.has(row)) {
            caught(row) ? hit++ : missed++;
            zoned += inZone(row) ? 1 : 0;
        }
    }

    document.getElementById('caption').textContent =
        'кадр ' + (frameAt + 1) + ' из ' + model['frames'].length
        + ' · бары ' + frame['from'] + '–' + frame['to']
        + ' · ' + shortDate(model['times'][frame['from']]) + ' → ' + shortDate(model['times'][frame['to'] - 1])
        + ' · уровней ' + frame['levels'].length
        + ' · впереди у линии ' + hit + ' из ' + (hit + missed)
        + ' · зон ' + (frame['zones'] || []).length + ', впереди в зоне ' + zoned + ' из ' + (hit + missed);
}

function shortTime(time) {
    return new Date(time).toLocaleString('ru-RU', {
        day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit'
    });
}

function shortDate(time) {
    return new Date(time).toLocaleDateString('ru-RU', {day: '2-digit', month: '2-digit', year: '2-digit'});
}
