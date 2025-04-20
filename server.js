const express = require('express');
const fetch = require('node-fetch'); // استدعاء مكتبة node-fetch
const app = express();
const PORT = process.env.PORT || 3000;

const PROTECTED_STREAM = "https://rotana.hibridcdn.net/rotananet/comedy_net-7Y83PP5adWixDF93/playlist.m3u8";

app.get('/stream', async (req, res) => {
    try {
        const response = await fetch(PROTECTED_STREAM, {
            headers: {
                "Referer": "https://rotana.net/",
                "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
            }
        });

        if (!response.ok) {
            res.status(response.status).send(`Error: ${response.statusText}`);
            return;
        }

        const stream = await response.text();
        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
        res.send(stream);
    } catch (error) {
        console.error(error);
        res.status(500).send('Internal Server Error');
    }
});

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});