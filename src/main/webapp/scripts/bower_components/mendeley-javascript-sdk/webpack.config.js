var webpack = require('webpack'),

    minify = process.argv[2] === '--minify',
    plugins = minify ? [new webpack.optimize.UglifyJsPlugin()] : [];

module.exports = {
    entry: {
        main: './lib/index.js'
    },
    output: {
        path: './dist',
        filename: 'standalone' + (minify ? '.min' : '') + '.js',

        library: 'MendeleySDK',
        libraryTarget: 'umd'
    },
    resolve: {
        modulesDirectories: [
            'node_modules',
            'bower_components',
            'lib'
        ]
    },
    devtool: minify ? "source-map" : "",
    plugins: plugins
};
