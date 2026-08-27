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

  const fullscreenVertexSource = `
    attribute vec2 aPosition;
    varying vec2 vUv;
    void main() {
      vUv = aPosition * 0.5 + 0.5;
      gl_Position = vec4(aPosition, 0.0, 1.0);
    }
  `;

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
  const translation = (x, y, z) => new Float32Array([
    1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1,
  ]);
  const rotationY = angle => {
    const value = angle * Math.PI / 180;
    const cosine = Math.cos(value);
    const sine = Math.sin(value);
    return new Float32Array([
      cosine, 0, -sine, 0, 0, 1, 0, 0, sine, 0, cosine, 0, 0, 0, 0, 1,
    ]);
  };
  const rotationZ = angle => {
    const value = angle * Math.PI / 180;
    const cosine = Math.cos(value);
    const sine = Math.sin(value);
    return new Float32Array([
      cosine, sine, 0, 0, -sine, cosine, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
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

  function createSphere(segments = 24, rings = 16) {
    const vertices = [];
    const indices = [];
    for (let ring = 0; ring <= rings; ring++) {
      const phi = Math.PI * ring / rings;
      for (let segment = 0; segment <= segments; segment++) {
        const theta = Math.PI * 2 * segment / segments;
        const x = Math.sin(phi) * Math.cos(theta);
        const y = Math.cos(phi);
        const z = Math.sin(phi) * Math.sin(theta);
        vertices.push(x, y, z, x, y, z);
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

  function uploadMesh(gl, mesh) {
    const vertexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, mesh.vertices, gl.STATIC_DRAW);
    const indexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, mesh.indices, gl.STATIC_DRAW);
    return { ...mesh, vertexBuffer, indexBuffer };
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

  function drawIndexed(gl, mesh) {
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, mesh.indexBuffer);
    gl.drawElements(gl.TRIANGLES, mesh.indices.length, gl.UNSIGNED_SHORT, 0);
  }

  function fitCanvas(gl) {
    const ratio = preview ? 1 : Math.min(window.devicePixelRatio || 1, 1.5);
    const width = Math.max(1, Math.round((canvas.clientWidth || window.innerWidth) * ratio));
    const height = Math.max(1, Math.round((canvas.clientHeight || window.innerHeight) * ratio));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    gl.viewport(0, 0, width, height);
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

  let drawFrame = () => {};

  function requestFrame() {
    if (!initialized || scheduled || !active || preview) return;
    scheduled = true;
    requestAnimationFrame(timestamp => {
      scheduled = false;
      if (!active) return;
      drawFrame(timestamp);
      requestFrame();
    });
  }

  async function main() {
    protocolSelfCheck();
    const gl = canvas.getContext('webgl', {
      alpha: false,
      antialias: true,
      depth: true,
      preserveDrawingBuffer: preview,
      powerPreference: 'high-performance',
    });
    if (!gl) throw new Error('WebGL unavailable');
    const shaderNames = [
      'thought_field_fragment.glsl',
      'thought_field_surface_vertex.glsl',
      'thought_field_part_vertex.glsl',
      'thought_field_glass_fragment.glsl',
    ];
    const [fieldFragment, surfaceVertex, partVertex, glassFragment] = await Promise.all(
      shaderNames.map(name => fetch(`${shaderBase}${name}`, { cache: 'no-store' }).then(response => {
        if (!response.ok) throw new Error(`Unable to load ${name}: ${response.status}`);
        return response.text();
      })),
    );

    const fieldProgram = createProgram(gl, fullscreenVertexSource, fieldFragment);
    const surfaceProgram = createProgram(gl, surfaceVertex, glassFragment);
    const partProgram = createProgram(gl, partVertex, glassFragment);
    const surface = uploadMesh(gl, createSurface());
    const sphere = uploadMesh(gl, createSphere());
    const quad = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, quad);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const fieldPosition = gl.getAttribLocation(fieldProgram, 'aPosition');
    const surfaceParam = gl.getAttribLocation(surfaceProgram, 'aParam');
    const partPosition = gl.getAttribLocation(partProgram, 'aPosition');
    const partNormal = gl.getAttribLocation(partProgram, 'aNormal');

    drawFrame = timestamp => {
      fitCanvas(gl);
      const seconds = fixedTime === null
        ? Math.max(0, timestamp - startTime) / 1000 * state.tempo
        : fixedTime;
      gl.clearColor(0.007, 0.016, 0.020, 1);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

      gl.useProgram(fieldProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, quad);
      gl.enableVertexAttribArray(fieldPosition);
      gl.vertexAttribPointer(fieldPosition, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(uniform(gl, fieldProgram, 'uResolution'), canvas.width, canvas.height);
      gl.uniform1f(uniform(gl, fieldProgram, 'uTime'), seconds);
      gl.uniform1f(uniform(gl, fieldProgram, 'uYaw'), yaw);
      gl.uniform1f(uniform(gl, fieldProgram, 'uZoom'), zoom);
      gl.uniform1f(uniform(gl, fieldProgram, 'uMood'), state.mood);
      gl.uniform1f(uniform(gl, fieldProgram, 'uGesture'), state.gesture);
      gl.uniform1f(uniform(gl, fieldProgram, 'uIntensity'), state.intensity);
      gl.uniform2f(uniform(gl, fieldProgram, 'uGaze'), state.gazeX, state.gazeY);
      gl.uniform4f(uniform(gl, fieldProgram, 'uShape'),
        state.faceWidth, state.eyeSize, state.eyeSpacing, state.headScale);
      gl.uniform2f(uniform(gl, fieldProgram, 'uFieldShape'), state.shoulderWidth, state.bodyHeight);
      gl.uniform4f(uniform(gl, fieldProgram, 'uExpression'),
        state.speaking, state.mouthWidth, state.facePresence, state.glow);
      gl.uniform1f(uniform(gl, fieldProgram, 'uSurfaceLayer'), 1);
      gl.disable(gl.DEPTH_TEST);
      gl.disable(gl.BLEND);
      gl.disable(gl.CULL_FACE);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

      const camera = [0, 0.15, zoom];
      const projection = perspective(36, canvas.width / canvas.height, 0.1, 20);
      const view = lookAt(camera, [0, 0.05, 0], [0, 1, 0]);
      const surfaceModel = multiply(rotationY(yaw), scale(0.88, 0.78, 0.88));
      const surfaceMvp = multiply(projection, multiply(view, surfaceModel));
      gl.useProgram(surfaceProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, surface.vertexBuffer);
      gl.enableVertexAttribArray(surfaceParam);
      gl.vertexAttribPointer(surfaceParam, 3, gl.FLOAT, false, 12, 0);
      gl.uniformMatrix4fv(uniform(gl, surfaceProgram, 'uMvp'), false, surfaceMvp);
      gl.uniformMatrix4fv(uniform(gl, surfaceProgram, 'uModel'), false, surfaceModel);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uTime'), seconds);
      gl.uniform2f(uniform(gl, surfaceProgram, 'uFieldShape'), state.shoulderWidth, state.bodyHeight);
      gl.uniform3f(uniform(gl, surfaceProgram, 'uInverseScale'), 1 / 0.88, 1 / 0.78, 1 / 0.88);
      gl.uniform3f(uniform(gl, surfaceProgram, 'uCameraPosition'), ...camera);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uIntensity'), state.intensity);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uGlow'), state.glow);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uSurfacePass'), 1);
      gl.enable(gl.DEPTH_TEST);
      gl.enable(gl.CULL_FACE);
      gl.enable(gl.BLEND);
      gl.depthMask(false);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
      [[0, 1], [2.05, 0.84]].forEach(([phase, layerScale]) => {
        gl.uniform1f(uniform(gl, surfaceProgram, 'uLayerPhase'), phase);
        gl.uniform1f(uniform(gl, surfaceProgram, 'uLayerScale'), layerScale);
        gl.cullFace(gl.FRONT);
        gl.uniform1f(uniform(gl, surfaceProgram, 'uBackFace'), 1);
        drawIndexed(gl, surface);
        gl.cullFace(gl.BACK);
        gl.uniform1f(uniform(gl, surfaceProgram, 'uBackFace'), 0);
        drawIndexed(gl, surface);
      });
      gl.colorMask(false, false, false, false);
      gl.disable(gl.BLEND);
      gl.depthMask(true);
      gl.cullFace(gl.BACK);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uLayerPhase'), 0);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uLayerScale'), 1);
      gl.uniform1f(uniform(gl, surfaceProgram, 'uBackFace'), 0);
      drawIndexed(gl, surface);
      gl.colorMask(true, true, true, true);

      const baseRotation = rotationY(yaw);
      const drawPart = (x, y, z, sx, sy, sz, angle, deform) => {
        const local = multiply(translation(x, y, z), multiply(rotationZ(angle), scale(sx, sy, sz)));
        const model = multiply(baseRotation, local);
        const mvp = multiply(projection, multiply(view, model));
        gl.useProgram(partProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, sphere.vertexBuffer);
        gl.enableVertexAttribArray(partPosition);
        gl.vertexAttribPointer(partPosition, 3, gl.FLOAT, false, 24, 0);
        gl.enableVertexAttribArray(partNormal);
        gl.vertexAttribPointer(partNormal, 3, gl.FLOAT, false, 24, 12);
        gl.uniformMatrix4fv(uniform(gl, partProgram, 'uMvp'), false, mvp);
        gl.uniformMatrix4fv(uniform(gl, partProgram, 'uModel'), false, model);
        gl.uniform1f(uniform(gl, partProgram, 'uTime'), seconds);
        gl.uniform1f(uniform(gl, partProgram, 'uDeform'), deform);
        gl.uniform3f(uniform(gl, partProgram, 'uInverseScale'), 1 / sx, 1 / sy, 1 / sz);
        gl.uniform3f(uniform(gl, partProgram, 'uCameraPosition'), ...camera);
        gl.uniform1f(uniform(gl, partProgram, 'uBackFace'), 0);
        gl.uniform1f(uniform(gl, partProgram, 'uIntensity'), state.intensity);
        gl.uniform1f(uniform(gl, partProgram, 'uGlow'), state.glow);
        gl.uniform1f(uniform(gl, partProgram, 'uSurfacePass'), 0);
        drawIndexed(gl, sphere);
      };
      const drawBone = (startX, startY, endX, endY, z, radius) => {
        const dx = endX - startX;
        const dy = endY - startY;
        const length = Math.hypot(dx, dy);
        drawPart((startX + endX) / 2, (startY + endY) / 2, z,
          radius, length * 0.62, radius, Math.atan2(-dx, dy) * 180 / Math.PI, 0.012);
      };

      if (gestureWithArm.has(Math.round(state.gesture))) {
        gl.enable(gl.BLEND);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
        gl.enable(gl.DEPTH_TEST);
        gl.enable(gl.CULL_FACE);
        gl.cullFace(gl.BACK);
        gl.depthMask(true);
        const point = Math.round(state.gesture) === 6;
        const side = point ? 1 : -1;
        const motion = Math.round(state.gesture) === 5
          ? Math.sin(seconds * 6.2) * 0.055 * state.intensity : 0;
        const shoulder = [side * 0.25, 0.31];
        const elbow = [side * 0.35, 0.39];
        const forearm = [side * 0.41, 0.50];
        const wrist = [point ? side * 0.62 : side * 0.45 + motion, point ? 0.38 : 0.66];
        drawBone(...shoulder, ...elbow, 0.24, 0.057);
        drawBone(...elbow, ...forearm, 0.26, 0.052);
        drawBone(...forearm, ...wrist, 0.28, 0.047);
        const palmX = wrist[0] + side * 0.030;
        const palmY = wrist[1] + 0.055;
        drawPart(palmX, wrist[1] + 0.035, 0.31, 0.12, 0.090, 0.062, -side * 22, 0.018);
        const fingers = point ? [
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
        fingers.forEach((finger, index) => {
          const mirror = -side;
          const root = [palmX + finger[0] * mirror, palmY + finger[1]];
          const joint = [palmX + finger[2] * mirror, palmY + finger[3]];
          const tip = [palmX + finger[4] * mirror, palmY + finger[5]];
          const z = 0.32 + (index - 2) * 0.008;
          drawBone(...root, ...joint, z, 0.022);
          drawBone(...joint, ...tip, z, 0.017);
        });
      }
      gl.depthMask(true);
      gl.flush();
    };

    installOrbitControls();
    canvas.addEventListener('webglcontextlost', event => {
      event.preventDefault();
      fail(new Error('WebGL context lost'));
    });
    initialized = true;
    drawFrame(performance.now());
    document.title = 'AGENTOS_AVATAR_READY';
    requestFrame();
  }

  function fail(error) {
    errorElement.textContent = error && (error.stack || error.message) || String(error);
    document.title = 'AGENTOS_AVATAR_ERROR';
  }

  main().catch(fail);
})();
