addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request));
});

async function handleRequest(request) {
  const stream_url = "https://rotana.hibridcdn.net/rotananet/comedy_net-7Y83PP5adWixDF93/playlist.m3u8";
  const response = await fetch(stream_url, {
    headers: {
      'Referer': 'https://rotana.net/',
      'User-Agent': 'Mozilla/5.0 (Linux; Android...)'
    }
  });
  return new Response(response.body, {
    headers: { 'Content-Type': 'application/vnd.apple.mpegurl' }
  });
}