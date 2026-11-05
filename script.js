async function searchMovie() {
  const title = document.getElementById('searchInput').value;
  const apiKey = 'thewdb';

  const res = await fetch(`https://www.omdbapi.com/?t=${title}&apikey=${apiKey}`);
  const data = await res.json();

  const resultDiv = document.getElementById('movieResult');

  if (data.Response === "False") {
    resultDiv.innerHTML = `<p>Couldn't found. 😢</p>`;
    return;
  }

  resultDiv.innerHTML = `
    <h2>${data.Title} (${data.Year})</h2>
    <img src="${data.Poster}" alt="Poster" />
    <p><strong>Tür:</strong> ${data.Genre}</p>
    <p><strong>IMDB:</strong> ${data.imdbRating}</p>
    <p><strong>Konu:</strong> ${data.Plot}</p>
  `;
}