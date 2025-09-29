google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(drawChart);

function drawChart() {
    fetch('PriceChart.json')
        .then(response => response.json())
        .then(jsonData => {
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
                    title: 'Price Chart',
                    subtitle: 'Prices over time'
                },
                width: 1800,
                height: 900,
                series: jsonData['options']['series'],

                // Настройки категориальной оси
                hAxis: {
                    title: 'Time',
                    showTextEvery: 2000, // Показывать каждую 50-ю метку
                }
            };

            var chart = new google.visualization.LineChart(document.getElementById('linechart_material'));
            chart.draw(data, options);
        })
        .catch(error => console.error('Error loading JSON data:', error));
}