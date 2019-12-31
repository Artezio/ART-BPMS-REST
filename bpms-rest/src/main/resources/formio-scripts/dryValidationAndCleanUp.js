require('jsdom-global')();
const {Formio} = require('formiojs');
global.Option = global.window.Option;

const body = document.body;
const [form, submission] = process.argv.slice(1).map(JSON.parse);

Formio.createForm(body, form)
    .then(instance => {
        instance.on('change',
            () => instance.submit()
                .then(
                    submit => console.info('submit:', submit),
                    error => console.error('error:', error)));
        instance.submission = submission;
    });
