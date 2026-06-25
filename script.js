const menuButton = document.querySelector('.menu-button');
const nav = document.querySelector('[data-nav]');

if (menuButton && nav) {
  menuButton.addEventListener('click', () => {
    const isOpen = nav.classList.toggle('open');
    menuButton.setAttribute('aria-expanded', String(isOpen));
  });

  nav.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      nav.classList.remove('open');
      menuButton.setAttribute('aria-expanded', 'false');
    });
  });
}

const priceCards = document.querySelectorAll('.price-card');

priceCards.forEach((card) => {
  const title = card.querySelector('h3');
  const link = card.querySelector('a.button');

  if (title && link && title.textContent.trim() === 'Пробная миссия') {
    link.href = 'trial-mission.html';
    link.textContent = 'Что входит';
    link.setAttribute('aria-label', 'Открыть описание пробной миссии');
  }
});

const tabs = document.querySelectorAll('[data-tab]');
const courses = document.querySelectorAll('[data-course]');

tabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    const target = tab.dataset.tab;

    tabs.forEach((item) => {
      const isActive = item === tab;
      item.classList.toggle('active', isActive);
      item.setAttribute('aria-selected', String(isActive));
    });

    courses.forEach((course) => {
      course.classList.toggle('active', course.dataset.course === target);
    });
  });
});

const form = document.querySelector('[data-form]');
const note = document.querySelector('[data-form-note]');

if (form && note) {
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const parent = data.get('parent');
    const age = data.get('age');
    const contact = data.get('contact');

    note.textContent = `Координаты приняты: ${parent}, ${age}, связь: ${contact}. Подключите Telegram‑бота или почту для реальной отправки.`;
    note.classList.add('success');
    form.reset();
  });
}

const radar = document.querySelector('.radar');

if (radar) {
  radar.style.cursor = 'crosshair';
  radar.title = 'Кликните, чтобы запустить мини-игру';

  radar.addEventListener('click', (event) => {
    event.preventDefault();
    openSpaceGame();
  });
}

function openSpaceGame() {
  if (document.querySelector('.space-game')) return;

  const game = document.createElement('div');
  game.className = 'space-game';
  game.innerHTML = `
    <canvas class="space-game-canvas"></canvas>
    <div class="space-game-panel">
      <div><b>ASTEROID RUN</b><span data-game-status>двигайте мышью, ЛКМ — огонь</span></div>
      <div class="space-game-stats">Очки: <span data-score>0</span> · Жизни: <span data-lives>3</span></div>
      <button type="button" data-close-game>×</button>
    </div>
  `;

  document.body.appendChild(game);
  document.body.style.overflow = 'hidden';

  const canvas = game.querySelector('canvas');
  const ctx = canvas.getContext('2d');
  const scoreNode = game.querySelector('[data-score]');
  const livesNode = game.querySelector('[data-lives]');
  const statusNode = game.querySelector('[data-game-status]');
  const closeButton = game.querySelector('[data-close-game]');

  let width = 0;
  let height = 0;
  let animationId = 0;
  let lastTime = performance.now();
  let spawnTimer = 0;
  let fireCooldown = 0;
  let score = 0;
  let lives = 3;
  let gameOver = false;

  const ship = { x: 120, y: 220, size: 44 };
  const mouse = { x: ship.x, y: ship.y };
  const bullets = [];
  const asteroids = [];
  const sparks = [];
  const stars = Array.from({ length: 120 }, () => ({ x: Math.random(), y: Math.random(), r: Math.random() * 1.8 + 0.3, s: Math.random() * 40 + 15 }));

  Object.assign(game.style, {
    position: 'fixed',
    inset: '0',
    zIndex: '9999',
    background: 'radial-gradient(circle at 25% 20%, rgba(35,230,255,.18), transparent 28rem), radial-gradient(circle at 80% 45%, rgba(255,176,0,.12), transparent 24rem), #02040c',
    cursor: 'crosshair'
  });

  Object.assign(canvas.style, {
    width: '100%',
    height: '100%',
    display: 'block'
  });

  const panelStyle = game.querySelector('.space-game-panel').style;
  Object.assign(panelStyle, {
    position: 'fixed',
    left: '18px',
    right: '18px',
    top: '18px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '14px',
    padding: '12px 14px',
    color: '#edf7ff',
    fontFamily: 'JetBrains Mono, monospace',
    border: '1px solid rgba(35,230,255,.45)',
    borderRadius: '16px',
    background: 'rgba(5,8,23,.72)',
    boxShadow: '0 0 28px rgba(35,230,255,.22)',
    backdropFilter: 'blur(10px)'
  });

  Object.assign(closeButton.style, {
    width: '38px',
    height: '38px',
    borderRadius: '50%',
    border: '1px solid rgba(255,78,122,.7)',
    background: 'rgba(255,78,122,.12)',
    color: '#ff86a3',
    fontSize: '24px',
    cursor: 'pointer'
  });

  const statusSpan = game.querySelector('[data-game-status]');
  Object.assign(statusSpan.style, { display: 'block', color: '#94a9c9', fontSize: '12px' });
  Object.assign(game.querySelector('.space-game-stats').style, { color: '#4cff9a', fontWeight: '700' });

  function resize() {
    const ratio = window.devicePixelRatio || 1;
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = Math.floor(width * ratio);
    canvas.height = Math.floor(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ship.size = Math.max(34, Math.min(width, height) * 0.05);
    ship.x = Math.max(ship.size, Math.min(width - ship.size, ship.x));
    ship.y = Math.max(ship.size, Math.min(height - ship.size, ship.y));
  }

  function shoot() {
    if (gameOver || fireCooldown > 0) return;
    fireCooldown = 0.14;
    bullets.push({ x: ship.x + ship.size * 0.85, y: ship.y, vx: Math.max(620, width * 0.75), life: 1.8 });
    addSparks(ship.x + ship.size * 0.85, ship.y, '#23e6ff', 5);
  }

  function spawnAsteroid() {
    const minSize = ship.size * 0.9;
    const maxSize = Math.max(minSize + 8, Math.min(width, height) * 0.40);
    const size = minSize + Math.random() * (maxSize - minSize);
    asteroids.push({
      x: width + size,
      y: 80 + Math.random() * Math.max(80, height - 160),
      r: size / 2,
      vx: -(95 + Math.random() * 150 + Math.max(0, 180 - size)),
      spin: (Math.random() - 0.5) * 2.5,
      angle: Math.random() * Math.PI * 2,
      hp: Math.max(1, Math.ceil(size / (ship.size * 1.25))),
      points: Math.round(size)
    });
  }

  function addSparks(x, y, color, count) {
    for (let i = 0; i < count; i += 1) {
      const a = Math.random() * Math.PI * 2;
      const speed = Math.random() * 160 + 40;
      sparks.push({ x, y, vx: Math.cos(a) * speed, vy: Math.sin(a) * speed, life: Math.random() * 0.35 + 0.2, color });
    }
  }

  function distance(a, b) {
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  function update(dt) {
    if (gameOver) return;

    fireCooldown = Math.max(0, fireCooldown - dt);
    ship.x += (mouse.x - ship.x) * Math.min(1, dt * 10);
    ship.y += (mouse.y - ship.y) * Math.min(1, dt * 10);
    ship.x = Math.max(ship.size, Math.min(width - ship.size, ship.x));
    ship.y = Math.max(ship.size + 42, Math.min(height - ship.size, ship.y));

    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnAsteroid();
      spawnTimer = Math.max(0.45, 1.25 - score / 2500) + Math.random() * 0.55;
    }

    bullets.forEach((bullet) => {
      bullet.x += bullet.vx * dt;
      bullet.life -= dt;
    });

    asteroids.forEach((asteroid) => {
      asteroid.x += asteroid.vx * dt;
      asteroid.angle += asteroid.spin * dt;
    });

    sparks.forEach((spark) => {
      spark.x += spark.vx * dt;
      spark.y += spark.vy * dt;
      spark.life -= dt;
    });

    for (let i = asteroids.length - 1; i >= 0; i -= 1) {
      const asteroid = asteroids[i];

      if (distance(ship, asteroid) < asteroid.r + ship.size * 0.45) {
        lives -= 1;
        livesNode.textContent = String(lives);
        addSparks(ship.x, ship.y, '#ff4e7a', 28);
        asteroids.splice(i, 1);
        if (lives <= 0) {
          gameOver = true;
          statusNode.textContent = 'миссия провалена — кликните по радару для новой попытки';
        }
        continue;
      }

      for (let j = bullets.length - 1; j >= 0; j -= 1) {
        const bullet = bullets[j];
        if (distance(bullet, asteroid) < asteroid.r + 5) {
          bullets.splice(j, 1);
          asteroid.hp -= 1;
          addSparks(bullet.x, bullet.y, '#ffb000', 8);
          if (asteroid.hp <= 0) {
            score += asteroid.points;
            scoreNode.textContent = String(score);
            addSparks(asteroid.x, asteroid.y, '#ffb000', 20);
            asteroids.splice(i, 1);
          }
          break;
        }
      }
    }

    for (let i = bullets.length - 1; i >= 0; i -= 1) {
      if (bullets[i].x > width + 80 || bullets[i].life <= 0) bullets.splice(i, 1);
    }

    for (let i = asteroids.length - 1; i >= 0; i -= 1) {
      if (asteroids[i].x < -asteroids[i].r * 2) asteroids.splice(i, 1);
    }

    for (let i = sparks.length - 1; i >= 0; i -= 1) {
      if (sparks[i].life <= 0) sparks.splice(i, 1);
    }
  }

  function drawShip() {
    ctx.save();
    ctx.translate(ship.x, ship.y);
    ctx.shadowColor = '#23e6ff';
    ctx.shadowBlur = 22;
    ctx.fillStyle = '#23e6ff';
    ctx.beginPath();
    ctx.moveTo(ship.size * 0.8, 0);
    ctx.lineTo(-ship.size * 0.55, -ship.size * 0.45);
    ctx.lineTo(-ship.size * 0.25, 0);
    ctx.lineTo(-ship.size * 0.55, ship.size * 0.45);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#4cff9a';
    ctx.fillRect(-ship.size * 0.45, -ship.size * 0.12, ship.size * 0.55, ship.size * 0.24);
    ctx.fillStyle = '#ffb000';
    ctx.beginPath();
    ctx.moveTo(-ship.size * 0.62, 0);
    ctx.lineTo(-ship.size * 1.05, -ship.size * 0.18);
    ctx.lineTo(-ship.size * 1.05, ship.size * 0.18);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }

  function drawAsteroid(asteroid) {
    ctx.save();
    ctx.translate(asteroid.x, asteroid.y);
    ctx.rotate(asteroid.angle);
    ctx.fillStyle = '#6c7284';
    ctx.strokeStyle = 'rgba(255,176,0,.65)';
    ctx.lineWidth = 2;
    ctx.shadowColor = 'rgba(255,176,0,.45)';
    ctx.shadowBlur = 16;
    ctx.beginPath();
    const chunks = 11;
    for (let i = 0; i < chunks; i += 1) {
      const a = (Math.PI * 2 * i) / chunks;
      const r = asteroid.r * (0.72 + ((i * 37) % 23) / 100);
      const x = Math.cos(a) * r;
      const y = Math.sin(a) * r;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  function draw() {
    ctx.clearRect(0, 0, width, height);

    ctx.fillStyle = '#02040c';
    ctx.fillRect(0, 0, width, height);

    ctx.fillStyle = 'rgba(237,247,255,.7)';
    stars.forEach((star) => {
      const x = (star.x * width - performance.now() * 0.012 * star.s) % width;
      ctx.beginPath();
      ctx.arc(x < 0 ? x + width : x, star.y * height, star.r, 0, Math.PI * 2);
      ctx.fill();
    });

    bullets.forEach((bullet) => {
      ctx.strokeStyle = '#4cff9a';
      ctx.shadowColor = '#4cff9a';
      ctx.shadowBlur = 18;
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.moveTo(bullet.x - 24, bullet.y);
      ctx.lineTo(bullet.x + 16, bullet.y);
      ctx.stroke();
      ctx.shadowBlur = 0;
    });

    asteroids.forEach(drawAsteroid);

    sparks.forEach((spark) => {
      ctx.globalAlpha = Math.max(0, spark.life * 2.4);
      ctx.fillStyle = spark.color;
      ctx.beginPath();
      ctx.arc(spark.x, spark.y, 3, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    });

    drawShip();

    if (gameOver) {
      ctx.fillStyle = 'rgba(2,4,12,.68)';
      ctx.fillRect(0, 0, width, height);
      ctx.textAlign = 'center';
      ctx.fillStyle = '#ffb000';
      ctx.font = '900 46px Exo 2, sans-serif';
      ctx.fillText('МИССИЯ ПРОВАЛЕНА', width / 2, height / 2 - 18);
      ctx.fillStyle = '#edf7ff';
      ctx.font = '700 20px Exo 2, sans-serif';
      ctx.fillText(`Очки: ${score}`, width / 2, height / 2 + 24);
      ctx.fillStyle = '#94a9c9';
      ctx.font = '500 16px JetBrains Mono, monospace';
      ctx.fillText('Закройте окно и кликните по радару для нового запуска', width / 2, height / 2 + 58);
    }
  }

  function frame(now) {
    const dt = Math.min(0.033, (now - lastTime) / 1000);
    lastTime = now;
    update(dt);
    draw();
    animationId = requestAnimationFrame(frame);
  }

  function closeGame() {
    cancelAnimationFrame(animationId);
    window.removeEventListener('resize', resize);
    window.removeEventListener('keydown', handleKeydown);
    document.body.style.overflow = '';
    game.remove();
  }

  function handlePointerMove(event) {
    mouse.x = event.clientX;
    mouse.y = event.clientY;
  }

  function handlePointerDown(event) {
    if (event.button === 0) shoot();
  }

  function handleKeydown(event) {
    if (event.key === 'Escape') closeGame();
  }

  closeButton.addEventListener('click', closeGame);
  game.addEventListener('mousemove', handlePointerMove);
  game.addEventListener('mousedown', handlePointerDown);
  game.addEventListener('touchmove', (event) => {
    const touch = event.touches[0];
    if (!touch) return;
    mouse.x = touch.clientX;
    mouse.y = touch.clientY;
  }, { passive: true });
  game.addEventListener('touchstart', (event) => {
    const touch = event.touches[0];
    if (touch) {
      mouse.x = touch.clientX;
      mouse.y = touch.clientY;
      shoot();
    }
  }, { passive: true });
  window.addEventListener('resize', resize);
  window.addEventListener('keydown', handleKeydown);

  resize();
  mouse.x = width * 0.18;
  mouse.y = height * 0.5;
  ship.x = mouse.x;
  ship.y = mouse.y;
  animationId = requestAnimationFrame(frame);
}
