google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(drawCharts);

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