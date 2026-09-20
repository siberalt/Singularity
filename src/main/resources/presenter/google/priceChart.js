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

function start() {
    fetch('LevelFrames.json')
        .then(response => response.ok ? response.json() : Promise.reject(response.status))
        .then(frames => startStepper(frames))
        .catch(() => drawCharts());
}

function startStepper(frames) {
    model = frames;
    lowAt = new Map(model['lows']);
    scale = {min: Math.min(...model['price']), max: Math.max(...model['price'])};
    chart = new google.visualization.ComboChart(document.getElementById('price_chart'));

    document.getElementById('volume_chart').hidden = true;
    document.getElementById('stepper').hidden = false;
    document.getElementById('legend').hidden = false;
    document.getElementById('legend').innerHTML =
        item('#1a73e8', 'цена в окне') + item('#9aa0a6', 'цена после окна')
        + item('#33691e', '3 касания') + item('#e65100', '4–5') + item('#b71c1c', '6 и больше')
        + item('#188038', 'минимум впереди у линии') + item('#d93025', 'минимум мимо')
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
    chart.draw(table, {
        chartArea: {width: '93%', height: '86%', top: 16, left: 70, right: 30},
        legend: {position: 'none'},
        interpolateNulls: false,
        series: series,
        hAxis: {showTextEvery: Math.ceil(rows.length / 12)},
        vAxis: document.getElementById('fixedScale').checked
            ? {viewWindow: {min: scale.min, max: scale.max}}
            : {}
    });
    writeCaption(frame, end, caught);
}

function addColumn(table, columns, series, title, value, style) {
    series[columns.length] = style;
    columns.push(value);
    table.addColumn('number', title);
}

function writeCaption(frame, end, caught) {
    let hit = 0;
    let missed = 0;

    for (let row = frame['to']; row < end; row++) {
        if (lowAt.has(row)) {
            caught(row) ? hit++ : missed++;
        }
    }

    document.getElementById('caption').textContent =
        'кадр ' + (frameAt + 1) + ' из ' + model['frames'].length
        + ' · бары ' + frame['from'] + '–' + frame['to']
        + ' · ' + shortDate(model['times'][frame['from']]) + ' → ' + shortDate(model['times'][frame['to'] - 1])
        + ' · уровней ' + frame['levels'].length
        + ' · впереди у линии ' + hit + ' из ' + (hit + missed);
}

function shortTime(time) {
    return new Date(time).toLocaleString('ru-RU', {
        day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit'
    });
}

function shortDate(time) {
    return new Date(time).toLocaleDateString('ru-RU', {day: '2-digit', month: '2-digit', year: '2-digit'});
}

// Без файла кадров страница остаётся прежней: один график из PriceChart.json.
function drawCharts() {
    fetch('PriceChart.json')
        .then(response => response.json())
        .then(jsonData => drawChart('Price Chart', 'price_chart', jsonData, 1000))
        .catch(error => console.error('Error loading JSON data:', error));
    fetch('VolumeChart.json')
        .then(response => response.json())
        .then(jsonData => drawChart('Volume Chart', 'volume_chart', jsonData, 300))
        .catch(error => console.error('Error loading JSON data:', error));
}

function drawChart(title, containerId, jsonData, height) {
    var data = new google.visualization.DataTable();

    for (let i = 0; i < jsonData['columns'].length; i++) {
        column = jsonData['columns'][i];

        if (column.type === 'date') {
            column.type = 'string'; // Изменяем тип на string
        }

        data.addColumn(column);
    }

    // Форматируем дату в строку
    const formatDate = (dateStr) => {
        const date = new Date(dateStr);
        return date.toLocaleString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    // Преобразуем данные в строковые метки
    const rows = jsonData['data'].map(item =>
        item.map((value, index) =>
            jsonData['columns'][index].type === 'date' ? formatDate(value) : value
        )
    );
    data.addRows(rows);

    var options = {
        chart: {
            title: title
        },
        chartArea: {
            width: "100%",
            height: height,
            top: 20,
            bottom: 45,
            left: 90,
            right: 300,
        },

        series: jsonData['options']['series'],

        // Настройки категориальной оси
        hAxis: {
            showTextEvery: 2000, // Показывать каждую 50-ю метку
        }
    };

    var chart = new google.visualization.ComboChart(document.getElementById(containerId));
    chart.draw(data, options);
}
