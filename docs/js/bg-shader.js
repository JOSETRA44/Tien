/* docs/js/bg-shader.js */

class BackgroundRenderer {
    #vertexSrc = "#version 300 es\nprecision highp float;\nin vec4 position;\nvoid main(){gl_Position=position;}";
    
    #fragmtSrc = `#version 300 es
    precision highp float;
    out vec4 O;
    uniform float time;
    uniform vec2 resolution;

    float noise(vec2 p) {
        return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
    }

    void main() {
        vec2 uv = gl_FragCoord.xy / resolution.xy;
        vec2 p = uv * 3.0 - 1.5;
        
        float t = time * 0.15;
        for(float i = 1.0; i < 4.0; i++) {
            p.x += 0.3 / i * cos(i * 2.5 * p.y + t);
            p.y += 0.3 / i * cos(i * 1.5 * p.x + t);
        }
        
        float intensity = cos(p.x + p.y + 1.0) * 0.5 + 0.5;
        
        // Deep monochrome with architectural tension
        vec3 color1 = vec3(0.06, 0.06, 0.07);
        vec3 color2 = vec3(0.12, 0.12, 0.16); 
        
        vec3 col = mix(color1, color2, intensity);
        col += (noise(uv * resolution) - 0.5) * 0.02; // Fine industrial grain
        
        O = vec4(col, 1.0);
    }`;

    #vertices = [-1, 1, -1, -1, 1, 1, 1, -1];

    constructor(canvas, dpr) {
        this.canvas = canvas;
        this.dpr = dpr || 1;
        this.gl = canvas.getContext("webgl2", { alpha: false, antialias: false });
        if (!this.gl) console.warn("WebGL2 not supported, falling back.");
        this.mouseMove = [0, 0];
        
        this.resize();
        window.addEventListener('resize', () => this.resize());
        document.addEventListener('mousemove', (e) => this.mouseMove = [e.clientX, e.clientY]);
    }

    compile(shader, source) {
        const gl = this.gl;
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
            console.error(gl.getShaderInfoLog(shader));
        }
    }

    setup() {
        const gl = this.gl;
        this.vs = gl.createShader(gl.VERTEX_SHADER);
        this.fs = gl.createShader(gl.FRAGMENT_SHADER);
        this.compile(this.vs, this.#vertexSrc);
        this.compile(this.fs, this.#fragmtSrc);
        this.program = gl.createProgram();
        gl.attachShader(this.program, this.vs);
        gl.attachShader(this.program, this.fs);
        gl.linkProgram(this.program);
    }

    init() {
        const { gl, program } = this;
        this.buffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(this.#vertices), gl.STATIC_DRAW);

        const position = gl.getAttribLocation(program, "position");
        gl.enableVertexAttribArray(position);
        gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);

        program.resolution = gl.getUniformLocation(program, "resolution");
        program.time = gl.getUniformLocation(program, "time");
    }

    resize() {
        const scale = this.dpr;
        this.canvas.width = window.innerWidth * scale;
        this.canvas.height = window.innerHeight * scale;
        this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    }

    render(now = 0) {
        const { gl, program, canvas } = this;
        if (!program || gl.getProgramParameter(program, gl.DELETE_STATUS)) return;

        gl.clearColor(0, 0, 0, 1);
        gl.clear(gl.COLOR_BUFFER_BIT);
        gl.useProgram(program);
        
        gl.uniform2f(program.resolution, canvas.width, canvas.height);
        gl.uniform1f(program.time, now * 0.001);

        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    // Start WebGL Background
    const canvas = document.getElementById('webgl-background');
    if (canvas) {
        // Optimized DPR for performance (downscale slightly if extremely high-density API allows)
        const dpr = Math.min(window.devicePixelRatio, 2) * 0.75; 
        const renderer = new BackgroundRenderer(canvas, dpr);
        renderer.setup();
        renderer.init();
        
        const loop = (now) => {
            renderer.render(now);
            requestAnimationFrame(loop);
        };
        requestAnimationFrame(loop);
    }
});
