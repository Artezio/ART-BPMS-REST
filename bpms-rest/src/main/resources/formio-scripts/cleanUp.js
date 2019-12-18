const cleanUpSubmissionData = require('./cleanUpSubmissionData');
const [form, data] = process.argv.slice(1).map(JSON.parse);

console.info(JSON.stringify(cleanUpSubmissionData(form, data)));