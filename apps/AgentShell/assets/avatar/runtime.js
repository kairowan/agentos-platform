(() => {
  'use strict';

  const PROTOCOL_VERSION = 1;
  const params = new URLSearchParams(location.search);
  const preview = params.get('preview') === '1';
  const fixedTime = preview ? Number(params.get('time') || 12.5) : null;
  const requestedShaderBase = params.get('shaderBase') || '/shaders/';
  const shaderBase = requestedShaderBase.startsWith('/') && !requestedShaderBase.includes('://')
    ? requestedShaderBase : '/shaders/';
  const gestureWithArm = new Set([3, 5, 6, 7, 8, 9]);
  const canvas = document.querySelector('#stage');
  const errorElement = document.querySelector('#error');
  let active = true;
  let initialized = false;
  let scheduled = false;
  let startTime = performance.now();
  let previousTimestamp = null;
  let yaw = -12;
  let zoom = 5.3;
  let state = preview ? {
    mood: 6, gesture: 5, intensity: 0.72, tempo: 1, gazeX: 0.08, gazeY: 0.02,
    speaking: 1, facePresence: 0.96, faceWidth: 0.5, eyeSize: 0.55,
    eyeSpacing: 0.5, mouthWidth: 0.5, headScale: 0.55, bodyHeight: 0.5,
    shoulderWidth: 0.5, glow: 0.45,
  } : {
    mood: 0, gesture: 0, intensity: 0.55, tempo: 1, gazeX: 0, gazeY: 0,
    speaking: 0, facePresence: 0.38, faceWidth: 0.5, eyeSize: 0.55,
    eyeSpacing: 0.5, mouthWidth: 0.5, headScale: 0.55, bodyHeight: 0.5,
    shoulderWidth: 0.5, glow: 0.2,
  };

  const bounded = (value, minimum, maximum, fallback) =>
    Number.isFinite(Number(value)) ? Math.min(maximum, Math.max(minimum, Number(value))) : fallback;

  function sanitizeState(input) {
    if (!input || Number(input.protocol) !== PROTOCOL_VERSION) return null;
    return {
      mood: bounded(input.mood, 0, 7, state.mood),
      gesture: bounded(input.gesture, 0, 9, state.gesture),
      intensity: bounded(input.intensity, 0, 1, state.intensity),
      tempo: bounded(input.tempo, 0.5, 1.8, state.tempo),
      gazeX: bounded(input.gazeX, -1, 1, state.gazeX),
      gazeY: bounded(input.gazeY, -1, 1, state.gazeY),
      speaking: bounded(input.speaking, 0, 1, state.speaking),
      facePresence: bounded(input.facePresence, 0, 1, state.facePresence),
      faceWidth: bounded(input.faceWidth, 0, 1, state.faceWidth),
      eyeSize: bounded(input.eyeSize, 0, 1, state.eyeSize),
      eyeSpacing: bounded(input.eyeSpacing, 0, 1, state.eyeSpacing),
      mouthWidth: bounded(input.mouthWidth, 0, 1, state.mouthWidth),
      headScale: bounded(input.headScale, 0, 1, state.headScale),
      bodyHeight: bounded(input.bodyHeight, 0, 1, state.bodyHeight),
      shoulderWidth: bounded(input.shoulderWidth, 0, 1, state.shoulderWidth),
      glow: bounded(input.glow, 0, 1, state.glow),
    };
  }

  function protocolSelfCheck() {
    const probe = sanitizeState({ protocol: PROTOCOL_VERSION, intensity: 4, tempo: -2, gazeX: NaN });
    if (!probe || probe.intensity !== 1 || probe.tempo !== 0.5 || probe.gazeX !== state.gazeX) {
      throw new Error('Renderer protocol bounds are broken');
    }
    if (sanitizeState({ protocol: PROTOCOL_VERSION + 1 }) !== null) {
      throw new Error('Unknown renderer protocol accepted');
    }
  }

  window.AgentOSAvatar = Object.freeze({
    applyState(input) {
      const next = sanitizeState(input);
      if (!next) return false;
      state = next;
      requestFrame();
      return true;
    },
    setActive(value) {
      active = Boolean(value);
      requestFrame();
    },
  });

  // ---------------------------------------------------------------- math ---

  function multiply(a, b) {
    const result = new Float32Array(16);
    for (let column = 0; column < 4; column++) {
      for (let row = 0; row < 4; row++) {
        let value = 0;
        for (let index = 0; index < 4; index++) {
          value += a[index * 4 + row] * b[column * 4 + index];
        }
        result[column * 4 + row] = value;
      }
    }
    return result;
  }

  function perspective(fovDegrees, aspect, near, far) {
    const f = 1 / Math.tan(fovDegrees * Math.PI / 360);
    const range = 1 / (near - far);
    return new Float32Array([
      f / aspect, 0, 0, 0,
      0, f, 0, 0,
      0, 0, (far + near) * range, -1,
      0, 0, 2 * far * near * range, 0,
    ]);
  }

  function normalize(vector) {
    const length = Math.hypot(...vector) || 1;
    return vector.map(value => value / length);
  }

  const cross = (a, b) => [
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
  ];
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

  function lookAt(eye, target, up) {
    const z = normalize([eye[0] - target[0], eye[1] - target[1], eye[2] - target[2]]);
    const x = normalize(cross(up, z));
    const y = cross(z, x);
    return new Float32Array([
      x[0], y[0], z[0], 0,
      x[1], y[1], z[1], 0,
      x[2], y[2], z[2], 0,
      -dot(x, eye), -dot(y, eye), -dot(z, eye), 1,
    ]);
  }

  const scale = (x, y, z) => new Float32Array([
    x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1,
  ]);
  const rotationY = angle => {
    const value = angle * Math.PI / 180;
    const cosine = Math.cos(value);
    const sine = Math.sin(value);
    return new Float32Array([
      cosine, 0, -sine, 0, 0, 1, 0, 0, sine, 0, cosine, 0, 0, 0, 0, 1,
    ]);
  };

  function createSurface(rings = 64, segments = 28) {
    const vertices = [];
    const indices = [];
    for (let ring = 0; ring <= rings; ring++) {
      for (let segment = 0; segment <= segments; segment++) {
        const angle = Math.PI * 2 * segment / segments;
        vertices.push(ring / rings, Math.cos(angle), Math.sin(angle));
      }
    }
    for (let ring = 0; ring < rings; ring++) {
      for (let segment = 0; segment < segments; segment++) {
        const first = ring * (segments + 1) + segment;
        const second = first + segments + 1;
        indices.push(first, second, first + 1, second, second + 1, first + 1);
      }
    }
    return { vertices: new Float32Array(vertices), indices: new Uint16Array(indices) };
  }

  function mulberry32(seed) {
    let a = seed >>> 0;
    return () => {
      a |= 0;
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  // Body-spline ports of the shared GLSL helpers, used to anchor the core
  // flare and the optical eyes to the deforming figure.
  function fieldCenterJs(y, time) {
    const lowerBody = 1 - smoothstep(-0.15, 0.58, y);
    return Math.sin(y * 2.02 + time * 0.24) * 0.055
      + Math.sin(y * 4.45 - time * 0.15) * 0.022
      + Math.sin((y + 1.05) * 2.7 - time * 0.10) * lowerBody * 0.140
      - smoothstep(0.68, 1.72, y) * 0.050;
  }

  function smoothstep(edge0, edge1, value) {
    const t = Math.min(1, Math.max(0, (value - edge0) / (edge1 - edge0)));
    return t * t * (3 - 2 * t);
  }

  const moodColors = {
    0: [1.0, 0.55, 0.20],
    1: [1.0, 0.73, 0.30],
    2: [0.32, 1.0, 0.78],
    3: [0.48, 0.67, 1.0],
    5: [1.0, 0.38, 0.30],
  };
  const warmWhite = [1.0, 0.93, 0.76];

  // ------------------------------------------------------------- GL glue ---

  function compile(gl, type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      throw new Error(gl.getShaderInfoLog(shader) || 'Shader compilation failed');
    }
    return shader;
  }

  function createProgram(gl, vertexSource, fragmentSource) {
    const program = gl.createProgram();
    gl.attachShader(program, compile(gl, gl.VERTEX_SHADER, vertexSource));
    gl.attachShader(program, compile(gl, gl.FRAGMENT_SHADER, fragmentSource));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      throw new Error(gl.getProgramInfoLog(program) || 'Program linking failed');
    }
    return program;
  }

  const uniformLocations = new WeakMap();
  function uniform(gl, program, name) {
    let locations = uniformLocations.get(program);
    if (!locations) {
      locations = new Map();
      uniformLocations.set(program, locations);
    }
    if (!locations.has(name)) locations.set(name, gl.getUniformLocation(program, name));
    return locations.get(name);
  }

  function uploadMesh(gl, mesh) {
    const vertexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, mesh.vertices, gl.STATIC_DRAW);
    const indexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, mesh.indices, gl.STATIC_DRAW);
    return { ...mesh, vertexBuffer, indexBuffer };
  }

  function createStrandMesh(strandBands) {
    // Ribbon mesh: three floats per vertex (strand, u, side) and two triangles
    // per segment, so shaders can expand each strand into a camera-facing band.
    const vertices = [];
    const indices = [];
    let vertexBase = 0;
    for (const [start, end, segments] of strandBands) {
      for (let strand = start; strand < end; strand++) {
        for (let step = 0; step <= segments; step++) {
          const u = step / segments;
          vertices.push(strand, u, -1);
          vertices.push(strand, u, 1);
        }
        for (let step = 0; step < segments; step++) {
          const a = vertexBase + step * 2;
          const b = a + 1;
          const c = a + 2;
          const d = a + 3;
          indices.push(a, b, c, b, d, c);
        }
        vertexBase += (segments + 1) * 2;
      }
    }
    return { vertices: new Float32Array(vertices), indices: new Uint32Array(indices) };
  }

  function createConstellation(seed) {
    const rand = mulberry32(seed);
    const nodes = [];
    for (let index = 0; index < 26; index++) {
      const background = rand() < 0.84;
      const angle = rand() * Math.PI * 2;
      const radius = 1.15 + 1.2 * rand();
      nodes.push([
        Math.cos(angle) * radius,
        (rand() - 0.47) * 4.3,
        background ? -(0.5 + 1.9 * rand()) : 0.55 + 0.8 * rand(),
        rand(),
      ]);
    }
    const seen = new Set();
    const indices = [];
    for (let i = 0; i < nodes.length; i++) {
      const neighbours = nodes
        .map((node, j) => ({ j, d: j === i ? 1e9 : Math.hypot(
          node[0] - nodes[i][0], node[1] - nodes[i][1], node[2] - nodes[i][2]) }))
        .sort((a, b) => a.d - b.d)
        .filter(entry => entry.d < 1.75)
        .slice(0, 2);
      for (const { j } of neighbours) {
        const key = i < j ? `${i}:${j}` : `${j}:${i}`;
        if (seen.has(key)) continue;
        seen.add(key);
        indices.push(i, j);
      }
    }
    const flat = [];
    for (const node of nodes) flat.push(...node);
    return {
      vertices: new Float32Array(flat),
      indices: new Uint16Array(indices),
      nodeCount: nodes.length,
    };
  }

  function createRenderTarget(gl, width, height, internalFormat, type) {
    const texture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texImage2D(gl.TEXTURE_2D, 0, internalFormat, width, height, 0, gl.RGBA, type, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    const framebuffer = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    return { texture, framebuffer, width, height };
  }

  function destroyRenderTarget(gl, target) {
    gl.deleteTexture(target.texture);
    gl.deleteFramebuffer(target.framebuffer);
  }

  const fullscreenVertexSource = `#version 300 es
    precision highp float;
    in vec2 aPosition;
    out vec2 vUv;
    void main() {
      vUv = aPosition * 0.5 + 0.5;
      gl_Position = vec4(aPosition, 0.0, 1.0);
    }
  `;

  // ------------------------------------------------------------- states ---

  let drawFrame = () => {};

  function requestFrame() {
    if (!initialized || scheduled || !active || preview) return;
    scheduled = true;
    requestAnimationFrame(timestamp => {
      scheduled = false;
      if (!active) return;
      try { drawFrame(timestamp); } catch (error) { fail(error); return; }
      requestFrame();
    });
  }

  function installOrbitControls() {
    const pointers = new Map();
    let previousDistance = 0;
    canvas.addEventListener('pointerdown', event => {
      canvas.setPointerCapture(event.pointerId);
      pointers.set(event.pointerId, [event.clientX, event.clientY]);
      previousDistance = 0;
    });
    canvas.addEventListener('pointermove', event => {
      const previous = pointers.get(event.pointerId);
      if (!previous) return;
      pointers.set(event.pointerId, [event.clientX, event.clientY]);
      if (pointers.size === 1) {
        yaw += (event.clientX - previous[0]) * 0.35;
      } else if (pointers.size === 2) {
        const values = [...pointers.values()];
        const distance = Math.hypot(values[0][0] - values[1][0], values[0][1] - values[1][1]);
        if (previousDistance > 0) zoom = bounded(zoom * previousDistance / distance, 3.8, 7.2, zoom);
        previousDistance = distance;
      }
      requestFrame();
    });
    const release = event => {
      pointers.delete(event.pointerId);
      previousDistance = 0;
    };
    canvas.addEventListener('pointerup', release);
    canvas.addEventListener('pointercancel', release);
  }

  // ------------------------------------------------------- hand skeleton ---

  function handPoseTargets(gesture, seconds, intensity) {
    const rounded = Math.round(gesture);
    const pointing = rounded === 6;
    const side = pointing ? 1 : -1;
    const mirror = -side;
    const motion = rounded === 5 ? Math.sin(seconds * 6.2) * 0.055 * intensity : 0;
    const arm = [
      [side * 0.25, 0.31, 0.24],
      [side * 0.35, 0.39, 0.26],
      [side * 0.41, 0.50, 0.28],
      [pointing ? side * 0.62 : side * 0.45 + motion, pointing ? 0.38 : 0.66, 0.30],
    ];
    const wrist = arm[3];
    const palmX = wrist[0] + side * 0.030;
    const palmY = wrist[1] + 0.055;
    const fingers = pointing ? [
      [-0.02, 0.07, -0.01, 0.20, 0.00, 0.34],
      [0.03, 0.05, 0.09, 0.10, 0.06, 0.16],
      [0.06, 0.02, 0.13, 0.04, 0.10, 0.10],
      [0.08, -0.01, 0.14, 0.00, 0.12, 0.06],
      [-0.08, 0.00, -0.14, 0.04, -0.16, 0.09],
    ] : [
      [-0.08, -0.01, -0.16, 0.02, -0.22, 0.07],
      [-0.06, 0.06, -0.15, 0.14, -0.20, 0.22],
      [-0.01, 0.08, -0.08, 0.19, -0.10, 0.28],
      [0.04, 0.07, 0.02, 0.18, 0.04, 0.26],
      [0.08, 0.04, 0.11, 0.13, 0.15, 0.20],
    ];
    const base = [palmX - 0.006 * mirror, palmY, 0.31];
    const fingerPoints = fingers.map((finger, index) => {
      const z = 0.31 + (index - 2) * 0.008;
      return [
        [base[0] + finger[0] * mirror, base[1] + finger[1], z],
        [base[0] + finger[2] * mirror, base[1] + finger[3], z],
        [base[0] + finger[4] * mirror, base[1] + finger[5], z],
      ];
    });
    return { arm, palm: [palmX, wrist[1] + 0.035, 0.31], fingers: fingerPoints };
  }

  const lerpPoint = (current, target, blend) =>
    current.map((value, index) => value + (target[index] - value) * blend);

  // ---------------------------------------------------------------- main ---

  async function main() {
    protocolSelfCheck();
    const gl = canvas.getContext('webgl2', {
      alpha: false,
      antialias: false,
      depth: true,
      preserveDrawingBuffer: preview,
      powerPreference: 'high-performance',
    });
    if (!gl) throw new Error('WebGL2 unavailable for the particle renderer');
    // WebGL2 VAOs keep each pass's attributes and index buffer together. Reusing
    // the default VAO leaked constellation indices into the glass pass on Android.
    const vertexArrays = new Map();
    const useProgram = program => {
      gl.useProgram(program);
      if (!vertexArrays.has(program)) vertexArrays.set(program, gl.createVertexArray());
      gl.bindVertexArray(vertexArrays.get(program));
    };
    let validationFrames = 2;
    const floatRender = gl.getExtension('EXT_color_buffer_float');
    const halfRender = gl.getExtension('EXT_color_buffer_half_float');
    const hdrFormat = floatRender || halfRender ? gl.RGBA16F : gl.RGBA8;
    const hdrType = floatRender || halfRender ? gl.HALF_FLOAT : gl.UNSIGNED_BYTE;
    const bloomThreshold = floatRender || halfRender ? 1.45 : 0.80;

    const prefix = '#version 300 es\nprecision highp float;\nprecision highp int;\n';
    const shaderNames = [
      'tf_shared.glsl',
      'tf_strand_vertex.glsl', 'tf_strand_fragment.glsl',
      'tf_spark_vertex.glsl', 'tf_spark_fragment.glsl',
      'tf_hand_vertex.glsl',
      'tf_bokeh_vertex.glsl', 'tf_bokeh_fragment.glsl',
      'tf_constellation_vertex.glsl', 'tf_constellation_fragment.glsl',
      'tf_flare_fragment.glsl',
      'tf_bright_fragment.glsl', 'tf_blur_fragment.glsl', 'tf_composite_fragment.glsl',
    ];
    const cacheBust = `v=${Date.now()}`;
    const sources = await Promise.all(shaderNames.map(name => fetch(`${shaderBase}${name}?${cacheBust}`, {
      cache: 'no-store',
    }).then(response => {
      if (!response.ok) throw new Error(`Unable to load ${name}: ${response.status}`);
      return response.text();
    })));
    const [shared, strandVertex, strandFragment, sparkVertex, sparkFragment, handVertex,
      bokehVertex, bokehFragment, constellationVertex, constellationFragment,
      flareFragment, brightFragment, blurFragment, compositeFragment] = sources;
    const expanded = source => prefix + shared + '\n' + source;
    const [glassVertexSource, glassFragmentSource] = await Promise.all(
      ['tf_glass_vertex.glsl', 'tf_glass_fragment.glsl'].map(name =>
        fetch(`${shaderBase}${name}?${cacheBust}`, { cache: 'no-store' }).then(response => {
          if (!response.ok) throw new Error(`Unable to load ${name}: ${response.status}`);
          return response.text();
        })));

    const strandProgram = createProgram(gl, expanded(strandVertex), expanded(strandFragment));
    const sparkProgram = createProgram(gl, expanded(sparkVertex), expanded(sparkFragment));
    const handProgram = createProgram(gl, expanded(handVertex), expanded(strandFragment));
    const handSparkProgram = createProgram(gl, expanded(handVertex), expanded(sparkFragment));
    const bokehProgram = createProgram(gl, expanded(bokehVertex), expanded(bokehFragment));
    const constellationProgram = createProgram(
      gl, expanded(constellationVertex), expanded(constellationFragment));
    const flareProgram = createProgram(gl, fullscreenVertexSource, expanded(flareFragment));
    const brightProgram = createProgram(gl, fullscreenVertexSource, expanded(brightFragment));
    const blurProgram = createProgram(gl, fullscreenVertexSource, expanded(blurFragment));
    const compositeProgram = createProgram(gl, fullscreenVertexSource, expanded(compositeFragment));
    const glassProgram = createProgram(gl, glassVertexSource, glassFragmentSource);

    const quad = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, quad);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);

    const surface = uploadMesh(gl, createSurface());

    const bodyStrands = 56;
    const headStrands = 20;
    const strandSegments = 96;
    const strandMesh = createStrandMesh([[0, bodyStrands + headStrands, strandSegments]]);
    const strandBuffer = uploadMesh(gl, strandMesh);
    const strandAttrib = gl.getAttribLocation(strandProgram, 'aStrand');
    const strandUAttrib = gl.getAttribLocation(strandProgram, 'aU');
    const strandSideAttrib = gl.getAttribLocation(strandProgram, 'aSide');

    const sparkCount = 26000;

    const handStrandSegments = 24;
    const handMesh = createStrandMesh([[0, 10, handStrandSegments], [10, 50, handStrandSegments]]);
    const handBuffer = uploadMesh(gl, handMesh);
    const handStrandAttrib = gl.getAttribLocation(handProgram, 'aStrand');
    const handUAttrib = gl.getAttribLocation(handProgram, 'aU');
    const handSideAttrib = gl.getAttribLocation(handProgram, 'aSide');
    const handSparkCount = 6500;

    const bokehQuads = 90;
    const bokehCount = bokehQuads * 6;

    const constellation = createConstellation(20260827);
    const constellationBuffer = uploadMesh(gl, constellation);
    const constellationPosAttrib = gl.getAttribLocation(constellationProgram, 'aPosition');
    const constellationSeedAttrib = gl.getAttribLocation(constellationProgram, 'aSeed');

    let fboWidth = 0;
    let fboHeight = 0;
    let sceneTarget = null;
    let bloomTargets = [];
    let depthBuffer = null;

    function recreateTargets(width, height) {
      if (sceneTarget) destroyRenderTarget(gl, sceneTarget);
      bloomTargets.forEach(target => destroyRenderTarget(gl, target));
      if (depthBuffer) gl.deleteRenderbuffer(depthBuffer);
      sceneTarget = createRenderTarget(gl, width, height, hdrFormat, hdrType);
      bloomTargets = [];
      for (let level = 0; level < 5; level++) {
        const divisor = 2 << level;
        bloomTargets.push(createRenderTarget(
          gl, Math.max(4, Math.floor(width / divisor)), Math.max(4, Math.floor(height / divisor)), hdrFormat, hdrType));
      }
      depthBuffer = gl.createRenderbuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, sceneTarget.framebuffer);
      gl.bindRenderbuffer(gl.RENDERBUFFER, depthBuffer);
      gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT16, width, height);
      gl.framebufferRenderbuffer(
        gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, depthBuffer);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.bindRenderbuffer(gl.RENDERBUFFER, null);
      fboWidth = width;
      fboHeight = height;
    }

    function bindTarget(target) {
      if (target) {
        gl.bindFramebuffer(gl.FRAMEBUFFER, target.framebuffer);
        gl.viewport(0, 0, target.width, target.height);
      } else {
        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        gl.viewport(0, 0, canvas.width, canvas.height);
      }
    }

    function drawFullscreen(program) {
      useProgram(program);
      gl.bindBuffer(gl.ARRAY_BUFFER, quad);
      const position = gl.getAttribLocation(program, 'aPosition');
      gl.enableVertexAttribArray(position);
      gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    function fitCanvas() {
      const ratio = preview ? 1 : Math.min(window.devicePixelRatio || 1, 1.5);
      const width = Math.max(1, Math.round((canvas.clientWidth || window.innerWidth) * ratio));
      const height = Math.max(1, Math.round((canvas.clientHeight || window.innerHeight) * ratio));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      if (width !== fboWidth || height !== fboHeight) recreateTargets(width, height);
    }

    // Smoothed state: the render command can jump between moods and gestures;
    // the field should glide instead of popping.
    let smooth = null;
    function advanceSmoothing(seconds, dt) {
      const blend = preview ? 1 : 1 - Math.exp(-dt * 6.0);
      const poseBlend = preview ? 1 : 1 - Math.exp(-dt * 9.0);
      const target = state;
      if (!smooth) {
        const pose = handPoseTargets(target.gesture, seconds, target.intensity);
        smooth = {
          intensity: target.intensity, facePresence: target.facePresence,
          speaking: target.speaking, glow: target.glow,
          gazeX: target.gazeX, gazeY: target.gazeY,
          accent: [...(moodColors[Math.round(target.mood)] || moodColors[0])],
          handGain: 0, pose,
        };
        return;
      }
      const mix = (current, goal) => current + (goal - current) * blend;
      smooth.intensity = mix(smooth.intensity, target.intensity);
      smooth.facePresence = mix(smooth.facePresence, target.facePresence);
      smooth.speaking = mix(smooth.speaking, target.speaking);
      smooth.glow = mix(smooth.glow, target.glow);
      smooth.gazeX = mix(smooth.gazeX, target.gazeX);
      smooth.gazeY = mix(smooth.gazeY, target.gazeY);
      const accentGoal = moodColors[Math.round(target.mood)] || moodColors[0];
      smooth.accent = smooth.accent.map((value, index) =>
        value + (accentGoal[index] - value) * blend);
      const goal = handPoseTargets(target.gesture, seconds, target.intensity);
      smooth.pose.arm = smooth.pose.arm.map((point, index) =>
        lerpPoint(point, goal.arm[index], poseBlend));
      smooth.pose.fingers = smooth.pose.fingers.map((triplet, index) =>
        triplet.map((point, joint) => lerpPoint(point, goal.fingers[index][joint], poseBlend)));
      const gestureActive = gestureWithArm.has(Math.round(target.gesture))
        && target.intensity > 0.05 ? 1 : 0;
      smooth.handGain = mix(smooth.handGain, gestureActive, blend);
    }

    drawFrame = timestamp => {
      fitCanvas();
      const dt = previousTimestamp === null || preview
        ? 0 : Math.min(0.1, (timestamp - previousTimestamp) / 1000);
      previousTimestamp = timestamp;
      const seconds = fixedTime === null
        ? Math.max(0, timestamp - startTime) / 1000 * state.tempo
        : fixedTime;
      advanceSmoothing(seconds, dt);

      const width = canvas.width;
      const height = canvas.height;
      const aspect = width / height;
      const camera = [0, 0.15, zoom];
      const projection = perspective(36, aspect, 0.1, 30);
      const view = lookAt(camera, [0, 0.05, 0], [0, 1, 0]);
      const yawTotal = yaw + Math.sin(seconds * 0.07) * 4;
      const model = rotationY(yawTotal);
      const viewProjection = multiply(projection, multiply(view, model));
      const yawRadians = yawTotal * Math.PI / 180;
      const cosineYaw = Math.cos(yawRadians);
      const sineYaw = Math.sin(yawRadians);
      const cameraModel = [
        cosineYaw * camera[0] - sineYaw * camera[2],
        camera[1],
        sineYaw * camera[0] + cosineYaw * camera[2],
      ];
      const pixelScale = height / 2 * projection[5];
      const ndcScale = [2 / width, 2 / height];
      const heightScale = 0.94 + state.bodyHeight * 0.12;

      const projectPoint = point => {
        const matrix = viewProjection;
        const cx = matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12];
        const cy = matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13];
        const cw = matrix[3] * point[0] + matrix[7] * point[1] + matrix[11] * point[2] + matrix[15];
        if (cw <= 0.001) return null;
        return {
          x: (cx / cw * 0.5 + 0.5) * width,
          y: (cy / cw * 0.5 + 0.5) * height,
          w: cw,
        };
      };

      // --- HDR scene -------------------------------------------------------
      bindTarget(sceneTarget);
      gl.clearColor(0.012, 0.008, 0.005, 1);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.disable(gl.DEPTH_TEST);
      gl.depthMask(false);
      gl.disable(gl.CULL_FACE);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE);

      const fieldShape = [state.shoulderWidth, state.bodyHeight];
      const common = program => {
        gl.uniform1f(uniform(gl, program, 'uTime'), seconds);
        gl.uniform2f(uniform(gl, program, 'uFieldShape'), fieldShape[0], fieldShape[1]);
        gl.uniformMatrix4fv(uniform(gl, program, 'uViewProjection'), false, viewProjection);
        gl.uniform3f(uniform(gl, program, 'uCameraPosition'),
          cameraModel[0], cameraModel[1], cameraModel[2]);
        gl.uniform3f(uniform(gl, program, 'uAccent'),
          smooth.accent[0], smooth.accent[1], smooth.accent[2]);
        gl.uniform3f(uniform(gl, program, 'uAccentWarm'),
          warmWhite[0], warmWhite[1], warmWhite[2]);
        gl.uniform1f(uniform(gl, program, 'uIntensity'), smooth.intensity);
        gl.uniform1f(uniform(gl, program, 'uTurbulence'), smooth.speaking);
        gl.uniform1f(uniform(gl, program, 'uFlowSpeed'), 1.0);
        gl.uniform1f(uniform(gl, program, 'uHeightScale'), heightScale);
        gl.uniform1f(uniform(gl, program, 'uPixelScale'), pixelScale);
        gl.uniform2f(uniform(gl, program, 'uNdcScale'), ndcScale[0], ndcScale[1]);
      };

      // Depth pre-pass for the glass shells first: the SwiftShader path proved
      // sensitive to interleaving additive geometry with masked depth writes,
      // so every depth write happens before any color in the frame.
      const surfaceModel = multiply(model, scale(0.88, 0.78, 0.88));
      const surfaceMvp = multiply(projection, multiply(view, surfaceModel));
      gl.enable(gl.DEPTH_TEST);
      gl.enable(gl.CULL_FACE);
      gl.depthMask(true);
      gl.disable(gl.BLEND);
      gl.colorMask(false, false, false, false);
      useProgram(glassProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, surface.vertexBuffer);
      const surfaceParam = gl.getAttribLocation(glassProgram, 'aParam');
      gl.enableVertexAttribArray(surfaceParam);
      gl.vertexAttribPointer(surfaceParam, 3, gl.FLOAT, false, 12, 0);
      gl.uniformMatrix4fv(uniform(gl, glassProgram, 'uMvp'), false, surfaceMvp);
      gl.uniformMatrix4fv(uniform(gl, glassProgram, 'uModel'), false, surfaceModel);
      gl.uniform1f(uniform(gl, glassProgram, 'uTime'), seconds);
      gl.uniform2f(uniform(gl, glassProgram, 'uFieldShape'), fieldShape[0], fieldShape[1]);
      gl.uniform3f(uniform(gl, glassProgram, 'uInverseScale'), 1 / 0.88, 1 / 0.78, 1 / 0.88);
      gl.uniform3f(uniform(gl, glassProgram, 'uCameraPosition'), ...camera);
      gl.uniform1f(uniform(gl, glassProgram, 'uIntensity'), smooth.intensity);
      gl.uniform1f(uniform(gl, glassProgram, 'uGlow'), smooth.glow);
      gl.uniform1f(uniform(gl, glassProgram, 'uSurfacePass'), 1);
      gl.uniform1f(uniform(gl, glassProgram, 'uLayerPhase'), 0);
      gl.uniform1f(uniform(gl, glassProgram, 'uLayerScale'), 1);
      gl.uniform1f(uniform(gl, glassProgram, 'uBackFace'), 0);
      gl.cullFace(gl.BACK);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, surface.indexBuffer);
      gl.drawElements(gl.TRIANGLES, surface.indices.length, gl.UNSIGNED_SHORT, 0);
      gl.colorMask(true, true, true, true);

      // Background: memory constellation links then nodes.
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE);
      gl.disable(gl.DEPTH_TEST);
      gl.depthMask(false);
      useProgram(constellationProgram);
      common(constellationProgram);
      gl.uniform1f(uniform(gl, constellationProgram, 'uLinkGain'), 1.0);
      gl.bindBuffer(gl.ARRAY_BUFFER, constellationBuffer.vertexBuffer);
      gl.enableVertexAttribArray(constellationPosAttrib);
      gl.vertexAttribPointer(constellationPosAttrib, 3, gl.FLOAT, false, 16, 0);
      gl.enableVertexAttribArray(constellationSeedAttrib);
      gl.vertexAttribPointer(constellationSeedAttrib, 1, gl.FLOAT, false, 16, 12);
      gl.uniform1f(uniform(gl, constellationProgram, 'uMode'), 0);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, constellationBuffer.indexBuffer);
      gl.drawElements(gl.LINES, constellation.indices.length, gl.UNSIGNED_SHORT, 0);
      gl.uniform1f(uniform(gl, constellationProgram, 'uMode'), 1);
      gl.drawArrays(gl.POINTS, 0, constellation.nodeCount);

      // Depth-of-field bokeh discs.
      useProgram(bokehProgram);
      common(bokehProgram);
      gl.uniform2f(uniform(gl, bokehProgram, 'uNdcScale'), ndcScale[0], ndcScale[1]);
      gl.drawArrays(gl.TRIANGLES, 0, bokehCount);

      // Glass shells rendered additively over the backdrop.
      gl.enable(gl.DEPTH_TEST);
      gl.enable(gl.CULL_FACE);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
      useProgram(glassProgram);
      gl.uniform1f(uniform(gl, glassProgram, 'uSurfacePass'), 1);
      [[0, 1], [2.05, 0.84]].forEach(([phase, layerScale]) => {
        gl.uniform1f(uniform(gl, glassProgram, 'uLayerPhase'), phase);
        gl.uniform1f(uniform(gl, glassProgram, 'uLayerScale'), layerScale);
        gl.cullFace(gl.FRONT);
        gl.uniform1f(uniform(gl, glassProgram, 'uBackFace'), 1);
        gl.drawElements(gl.TRIANGLES, surface.indices.length, gl.UNSIGNED_SHORT, 0);
        gl.cullFace(gl.BACK);
        gl.uniform1f(uniform(gl, glassProgram, 'uBackFace'), 0);
        gl.drawElements(gl.TRIANGLES, surface.indices.length, gl.UNSIGNED_SHORT, 0);
      });

      // Foreground silk strands, spark field, particle hand.
      gl.disable(gl.DEPTH_TEST);
      gl.depthMask(false);
      gl.blendFunc(gl.ONE, gl.ONE);
      useProgram(strandProgram);
      common(strandProgram);
      gl.uniform1f(uniform(gl, strandProgram, 'uHeadStart'), bodyStrands);
      gl.uniform1f(uniform(gl, strandProgram, 'uHeadScale'), state.headScale);
      gl.bindBuffer(gl.ARRAY_BUFFER, strandBuffer.vertexBuffer);
      gl.enableVertexAttribArray(strandAttrib);
      gl.vertexAttribPointer(strandAttrib, 1, gl.FLOAT, false, 12, 0);
      gl.enableVertexAttribArray(strandUAttrib);
      gl.vertexAttribPointer(strandUAttrib, 1, gl.FLOAT, false, 12, 4);
      gl.enableVertexAttribArray(strandSideAttrib);
      gl.vertexAttribPointer(strandSideAttrib, 1, gl.FLOAT, false, 12, 8);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, strandBuffer.indexBuffer);
      gl.drawElements(gl.TRIANGLES, strandBuffer.indices.length, gl.UNSIGNED_INT, 0);

      useProgram(sparkProgram);
      common(sparkProgram);
      gl.drawArrays(gl.POINTS, 0, sparkCount);

      if (smooth.handGain > 0.01) {
        const armChain = new Float32Array(smooth.pose.arm.flat());
        useProgram(handProgram);
        common(handProgram);
        gl.uniform1f(uniform(gl, handProgram, 'uHandGain'), smooth.handGain);
        gl.uniform1f(uniform(gl, handProgram, 'uMode'), 0);
        gl.uniform3fv(uniform(gl, handProgram, 'uArmChain'), armChain);
        gl.uniform3fv(uniform(gl, handProgram, 'uFingerRoot'),
          new Float32Array(smooth.pose.fingers.map(f => f[0]).flat()));
        gl.uniform3fv(uniform(gl, handProgram, 'uFingerJoint'),
          new Float32Array(smooth.pose.fingers.map(f => f[1]).flat()));
        gl.uniform3fv(uniform(gl, handProgram, 'uFingerTip'),
          new Float32Array(smooth.pose.fingers.map(f => f[2]).flat()));
        gl.bindBuffer(gl.ARRAY_BUFFER, handBuffer.vertexBuffer);
        gl.enableVertexAttribArray(handStrandAttrib);
        gl.vertexAttribPointer(handStrandAttrib, 1, gl.FLOAT, false, 12, 0);
        gl.enableVertexAttribArray(handUAttrib);
        gl.vertexAttribPointer(handUAttrib, 1, gl.FLOAT, false, 12, 4);
        gl.enableVertexAttribArray(handSideAttrib);
        gl.vertexAttribPointer(handSideAttrib, 1, gl.FLOAT, false, 12, 8);
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, handBuffer.indexBuffer);
        gl.drawElements(gl.TRIANGLES, handBuffer.indices.length, gl.UNSIGNED_INT, 0);
        useProgram(handSparkProgram);
        common(handSparkProgram);
        gl.uniform1f(uniform(gl, handSparkProgram, 'uHandGain'), smooth.handGain);
        gl.uniform1f(uniform(gl, handSparkProgram, 'uMode'), 1);
        gl.uniform3fv(uniform(gl, handSparkProgram, 'uArmChain'), armChain);
        gl.uniform3fv(uniform(gl, handSparkProgram, 'uFingerRoot'),
          new Float32Array(smooth.pose.fingers.map(f => f[0]).flat()));
        gl.uniform3fv(uniform(gl, handSparkProgram, 'uFingerJoint'),
          new Float32Array(smooth.pose.fingers.map(f => f[1]).flat()));
        gl.uniform3fv(uniform(gl, handSparkProgram, 'uFingerTip'),
          new Float32Array(smooth.pose.fingers.map(f => f[2]).flat()));
        gl.drawArrays(gl.POINTS, 0, handSparkCount);
      }

      // Amber core flare and optical eyes on top.
      const coreProjection = projectPoint(
        [fieldCenterJs(0.15 * heightScale, seconds), 0.15 * heightScale, 0]);
      const eyeCenter = [
        fieldCenterJs(1.20 * heightScale, seconds), 1.20 * heightScale, 0];
      const eyeGapWorld = 0.10 + state.eyeSpacing * 0.09;
      const leftEye = projectPoint([eyeCenter[0] - eyeGapWorld, eyeCenter[1], 0]);
      const rightEye = projectPoint([eyeCenter[0] + eyeGapWorld, eyeCenter[1], 0]);
      const centerEye = projectPoint(eyeCenter);
      if (coreProjection && centerEye) {
        const sigmaWorld = 0.022 + state.headScale * 0.011;
        const sigmaPx = sigmaWorld * pixelScale / Math.max(centerEye.w, 0.1);
        const gapPx = leftEye && rightEye
          ? Math.abs(rightEye.x - leftEye.x) / 2 : sigmaPx * 2.2;
        const eyePx = [
          centerEye.x + smooth.gazeX * gapPx * 0.45,
          centerEye.y - smooth.gazeY * sigmaPx * 1.6,
        ];
        const blink = Math.sin(seconds * 0.69);
        useProgram(flareProgram);
        gl.uniform2f(uniform(gl, flareProgram, 'uCorePx'),
          coreProjection.x, coreProjection.y);
        gl.uniform2f(uniform(gl, flareProgram, 'uEyePx'), eyePx[0], eyePx[1]);
        gl.uniform1f(uniform(gl, flareProgram, 'uEyeGapPx'), gapPx);
        gl.uniform1f(uniform(gl, flareProgram, 'uEyeSigmaPx'), sigmaPx);
        gl.uniform1f(uniform(gl, flareProgram, 'uBlink'),
          Math.abs(blink) > 0.12 ? 1.0 : 0.0);
        gl.uniform1f(uniform(gl, flareProgram, 'uFacePresence'), smooth.facePresence);
        gl.uniform1f(uniform(gl, flareProgram, 'uFlareGain'),
          (0.45 + 0.40 * smooth.intensity) * (0.7 + smooth.glow * 0.45));
        gl.uniform1f(uniform(gl, flareProgram, 'uSpeaking'), smooth.speaking);
        gl.uniform1f(uniform(gl, flareProgram, 'uFlareScale'), height / 2048);
        gl.uniform3f(uniform(gl, flareProgram, 'uAccent'),
          smooth.accent[0], smooth.accent[1], smooth.accent[2]);
        drawFullscreen(flareProgram);
      }

      // --- bloom chain -----------------------------------------------------
      gl.disable(gl.BLEND);
      useProgram(brightProgram);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, sceneTarget.texture);
      gl.uniform1i(uniform(gl, brightProgram, 'uTex'), 0);
      gl.uniform1f(uniform(gl, brightProgram, 'uThreshold'), bloomThreshold);
      bindTarget(bloomTargets[0]);
      drawFullscreen(brightProgram);
      useProgram(blurProgram);
      gl.uniform1i(uniform(gl, blurProgram, 'uTex'), 0);
      for (let level = 1; level < bloomTargets.length; level++) {
        const source = bloomTargets[level - 1];
        gl.bindTexture(gl.TEXTURE_2D, source.texture);
        gl.uniform1f(uniform(gl, blurProgram, 'uDown'), 1);
        gl.uniform2f(uniform(gl, blurProgram, 'uTexel'), 1 / source.width, 1 / source.height);
        bindTarget(bloomTargets[level]);
        drawFullscreen(blurProgram);
      }
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE);
      for (let level = bloomTargets.length - 2; level >= 0; level--) {
        const source = bloomTargets[level + 1];
        gl.bindTexture(gl.TEXTURE_2D, source.texture);
        gl.uniform1f(uniform(gl, blurProgram, 'uDown'), 0);
        gl.uniform2f(uniform(gl, blurProgram, 'uTexel'), 1 / source.width, 1 / source.height);
        bindTarget(bloomTargets[level]);
        drawFullscreen(blurProgram);
      }

      // --- composite -------------------------------------------------------
      gl.disable(gl.BLEND);
      bindTarget(null);
      useProgram(compositeProgram);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, sceneTarget.texture);
      gl.uniform1i(uniform(gl, compositeProgram, 'uScene'), 0);
      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, bloomTargets[0].texture);
      gl.uniform1i(uniform(gl, compositeProgram, 'uBloom'), 1);
      gl.uniform1f(uniform(gl, compositeProgram, 'uBloomStrength'), 1.05 + smooth.glow * 0.9);
      gl.uniform1f(uniform(gl, compositeProgram, 'uExposure'), 1.25);
      gl.uniform2f(uniform(gl, compositeProgram, 'uResolution'), width, height);
      drawFullscreen(compositeProgram);
      gl.activeTexture(gl.TEXTURE0);
      gl.flush();
      if (validationFrames-- > 0 && gl.getError() !== gl.NO_ERROR) {
        throw new Error('Renderer failed its first/two-frame WebGL state check');
      }
    };

    installOrbitControls();
    canvas.addEventListener('webglcontextlost', event => {
      event.preventDefault();
      fail(new Error('WebGL context lost'));
    });
    initialized = true;
    drawFrame(performance.now());
    if (preview) drawFrame(performance.now()); // Check state carried between frames, too.
    document.title = 'AGENTOS_AVATAR_READY';
    requestFrame();
  }

  function fail(error) {
    active = false;
    errorElement.textContent = error && (error.stack || error.message) || String(error);
    document.title = 'AGENTOS_AVATAR_ERROR';
  }

  main().catch(fail);
})();
