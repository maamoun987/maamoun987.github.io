const express = require('express');
const fetch = require('node-fetch'); // لإرسال الطلبات إلى القناة
const app = express();
const PORT = process.env.PORT || 3000; // استخدم المنفذ الخاص بـ Render أو المنفذ الافتراضي

// رابط القناة المحمية
const PROTECTED_STREAM = "https://rotana.hibridcdn.net/rotananet/comedy_net-7Y83PP5adWixDF93/playlist.m3u8";

// إعداد خادم Node.js
app.get('/stream', async (req, res) => {
    try {
        // إرسال طلب إلى القناة مع إضافة الهيدر المطلوب
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

        // إعادة البيانات إلى المستخدم
        const stream = await response.text();
        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl'); // نوع الملف M3U8
        res.send(stream);
    } catch (error) {
        console.error(error);
        res.status(500).send('Internal Server Error');
    }
});

// تشغيل الخادم
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});