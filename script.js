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
      item.classList.toggle('active', item === tab);
      item.setAttribute('aria-selected', String(item === tab));
    });

    courses.forEach((course) => {
      course.classList.toggle('active', course.dataset.course === target);
    });
  });
});

const form = document.querySelector('[data-form]');
const formNote = document.querySelector('[data-form-note]');

if (form && formNote) {
  form.addEventListener('submit', (event) => {
    event.preventDefault();

    const data = new FormData(form);
    const parent = data.get('parent');
    const age = data.get('age');
    const contact = data.get('contact');

    formNote.textContent = `Заявка подготовлена: ${parent}, ${age}, контакт: ${contact}. Подключите отправку в Telegram, на почту или в CRM.`;
    formNote.classList.add('success');
    form.reset();
  });
}
