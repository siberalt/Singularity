google.charts.load('current', {'packages':['corechart']});
google.charts.setOnLoadCallback(drawChart);

function drawChart() {
  fetch('PriceChart.json')
    .then(response => response.json())
    .then(jsonData => {
      var data = new google.visualization.DataTable();

      // Заменяем 'datetime' на 'string' для категориальной оси
      data.addColumn('string', 'Time'); // 📅 Категориальная ось (фиксированные расстояния)
      data.addColumn('number', 'Price');
      data.addColumn('number', 'Support');

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
      const rows = jsonData.map(item => [formatDate(item[0]), item[1], item[2]]);
      data.addRows(rows);

      var options = {
        chart: {
          title: 'Price Chart',
          subtitle: 'Prices over time'
        },
        width: 1300,
        height: 700,
            series: {
            0: { lineWidth: 2}, // Стиль основной серии
              1: {
                lineWidth: 3,
                color: '#00FF00', // Зеленый цвет отрезка
              }
            },

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