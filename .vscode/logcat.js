const { spawn } = require('child_process');

const showAll = process.argv[2] === 'all';
const adbArgs = showAll ? ['logcat'] : ['logcat', '--package=com.waenhancer'];

console.log("---------------------------------------------------------");
console.log(`Streaming Android Logcat (${showAll ? 'All logs' : 'com.waenhancer'}) to Debug Console`);
console.log("Press the Stop button (Red Square) to terminate streaming.");
console.log("---------------------------------------------------------");

const logcat = spawn('adb', adbArgs);

logcat.stdout.on('data', (data) => {
    process.stdout.write(data.toString());
});

logcat.stderr.on('data', (data) => {
    process.stderr.write(data.toString());
});

logcat.on('error', (err) => {
    console.error("Error: Could not start 'adb'. Please ensure Android SDK platform-tools are in your PATH.", err);
});

logcat.on('close', (code) => {
    console.log(`\nLogcat stream ended (exit code: ${code})`);
});

process.on('SIGINT', () => {
    logcat.kill();
    process.exit(0);
});

process.on('SIGTERM', () => {
    logcat.kill();
    process.exit(0);
});
