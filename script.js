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
