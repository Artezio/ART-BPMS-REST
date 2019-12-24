require('jsdom-global')();
const { Formio } = require('formiojs');

const body = document.body;
const [form, submission] = process.argv.slice(1).map(JSON.parse);

Formio.createForm(body, form)
    .then(instance => {
        instance.on('error', error => { console.error(error) });
        instance.on('submit', submit => { console.info(JSON.stringify(submit)) });
        instance.on('change', () => instance.submit());
        instance.submission = submission;
    });
