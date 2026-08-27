package com.aquarium.neon

object Shaders {

    private const val COMMON_FS = """
        uniform vec3  uFogColor;
        uniform float uFogDensity;
        uniform vec3  uLightColor;
        uniform vec3  uAmbientColor;

        vec3 applyFog(vec3 color, float viewDist) {
            float f = 1.0 - exp(-uFogDensity * viewDist);
            return mix(color, uFogColor, clamp(f, 0.0, 1.0));
        }

        vec3 tonemapACES(vec3 x) {
            const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }

        // Каустическая сетка: три слоя расходящихся волн, скользящие по поверхности
        float causticField(vec3 p, float t) {
            vec2 q = p.xz * 0.5 + p.y * 0.08;
            float v = 0.0;
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                vec2 o = vec2(sin(t * 0.55 + fi * 2.1), cos(t * 0.47 + fi * 1.7));
                v += abs(sin(q.x * 2.4 + o.x * 2.0 + t * 0.8) + sin(q.y * 2.2 + o.y * 2.0 - t * 0.7));
                q *= 1.7;
            }
            return pow(1.0 - clamp(v / 3.0, 0.0, 1.0), 4.5);
        }
    """

    // ═══════════════════════════ РЫБЫ ═══════════════════════════
    val FISH_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;

        uniform mat4  uMVP;
        uniform mat4  uModel;
        uniform vec3  uCameraPos;
        uniform float uTime;
        uniform float uSwimSpeed;
        uniform float uWaveFreq;
        uniform float uWaveAmp;
        uniform float uBodyLen;
        uniform float uHeadZ;
        uniform float uFinTension;   // 0 спокойна .. 1 плавники растопырены от страха
        uniform int   uForm;         // 0 fusiform 1 disc 2 eel 3 manta 4 jelly 5 shark 6 lionfish

        out vec3  vWorldPos;
        out vec3  vNormal;
        out vec2  vTexCoord;
        out vec3  vLocal;
        out float vViewDist;
        out float vBodyU;

        void main() {
            vec3 pos = aPosition;
            vec3 nrm = aNormal;

            float u = clamp((uHeadZ - pos.z) / max(uBodyLen, 0.001), 0.0, 1.0);
            vBodyU = u;

            // Испуганная рыба распускает плавники: точки дальше от оси расходятся сильнее
            float spread = uFinTension * 0.16;
            pos.x *= 1.0 + spread * step(0.30, abs(pos.x));
            pos.y *= 1.0 + spread * step(0.30, abs(pos.y));

            if (uForm == 3) {
                // Скат: бегущая от корпуса к кончику крыла волна
                float ax = abs(pos.x);
                float phase = uTime * uSwimSpeed - ax * 2.1;
                float flap = sin(phase) * ax * ax * uWaveAmp * 2.2;
                pos.y += flap;
                float slope = cos(phase) * 2.0 * ax * uWaveAmp * 2.2 * sign(pos.x);
                nrm = normalize(nrm - vec3(slope, 0.0, 0.0));
            } else if (uForm == 4) {
                // Медуза: реактивный выброс воды из-под купола
                float pulse = sin(uTime * uSwimSpeed);
                float sharp = pow(pulse * 0.5 + 0.5, 2.6) - 0.34;
                float bell = smoothstep(-0.5, 0.9, pos.z);
                pos.xy *= (1.0 - sharp * 0.24 * bell);
                pos.z  -= sharp * 0.30 * bell;
                float tent = smoothstep(0.1, -2.4, pos.z);
                pos.x += sin(uTime * 1.7 + pos.z * 2.2) * tent * 0.32;
                pos.y += cos(uTime * 1.3 + pos.z * 1.8) * tent * 0.24;
            } else if (uForm == 2) {
                // Угорь: ундуляция по всей длине, амплитуда растёт к хвосту линейно
                float amp = uWaveAmp * (0.25 + 0.75 * u);
                float phase = uTime * uSwimSpeed - u * uWaveFreq;
                pos.x += sin(phase) * amp;
                float slope = cos(phase) * amp * uWaveFreq / max(uBodyLen, 0.001);
                nrm = normalize(nrm + vec3(0.0, 0.0, -slope * 0.6));
            } else {
                // Карангиформное плавание: голова почти неподвижна, работает задняя треть
                float amp = uWaveAmp * pow(u, 2.1);
                float phase = uTime * uSwimSpeed - u * uWaveFreq;
                pos.x += sin(phase) * amp;
                float slope = cos(phase) * amp * uWaveFreq / max(uBodyLen, 0.001);
                nrm = normalize(nrm + vec3(0.0, 0.0, -slope * 0.55));
            }

            vLocal = pos;
            vec4 world = uModel * vec4(pos, 1.0);
            vWorldPos  = world.xyz;
            vNormal    = normalize(mat3(uModel) * nrm);
            vTexCoord  = aTexCoord;
            vViewDist  = distance(uCameraPos, world.xyz);
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    val FISH_FS = """
        #version 300 es
        precision highp float;

        in vec3  vWorldPos;
        in vec3  vNormal;
        in vec2  vTexCoord;
        in vec3  vLocal;
        in float vViewDist;
        in float vBodyU;

        uniform vec3  uPrimary;
        uniform vec3  uGlow;
        uniform vec3  uAccent;
        uniform vec3  uLightDir;
        uniform vec3  uCameraPos;
        uniform float uTime;
        uniform float uPanic;
        uniform float uBloodTint;
        uniform float uStripeFreq;
        uniform float uStripeSharp;
        uniform float uOpacity;
        uniform float uMetallic;
        uniform vec4  uEye;          // xyz локальная позиция глаза, w радиус (0 = глаза нет)
        uniform int   uPattern;      // 0 полосы 1 пятна 2 сетка 3 однотонная 4 градиент
        uniform int   uForm;
        $COMMON_FS

        out vec4 fragColor;

        float hash21(vec2 p) { return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            if (dot(N, V) < 0.0) N = -N;         // тонкие плавники освещаются с двух сторон

            vec3 L = normalize(uLightDir);
            vec3 H = normalize(L + V);

            float diff = max(dot(N, L), 0.0);
            float spec = pow(max(dot(N, H), 0.0), mix(36.0, 110.0, uMetallic));
            float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
            float back = pow(max(dot(-N, L), 0.0), 2.1) * 0.42;   // просвет тела насквозь

            // ── Рисунок окраски ─────────────────────────────────────────────
            float pat;
            if (uPattern == 0) {
                pat = sin(vBodyU * uStripeFreq * 6.28318) * 0.5 + 0.5;
                pat = smoothstep(0.5 - uStripeSharp, 0.5 + uStripeSharp, pat);
            } else if (uPattern == 1) {
                vec2 cell = floor(vec2(vTexCoord.x * 14.0, vBodyU * 20.0));
                float r = hash21(cell);
                vec2 f = fract(vec2(vTexCoord.x * 14.0, vBodyU * 20.0)) - 0.5;
                pat = step(0.30, r) * (1.0 - smoothstep(0.12, 0.30, dot(f, f)));
            } else if (uPattern == 2) {
                float a = sin(vTexCoord.x * 34.0) * sin(vBodyU * 46.0);
                pat = smoothstep(-0.1, 0.35, a);
            } else if (uPattern == 4) {
                pat = smoothstep(0.15, 0.85, vBodyU);
            } else {
                pat = 0.0;
            }

            float scales = sin(vTexCoord.x * 78.0) * sin(vBodyU * 150.0) * 0.5 + 0.5;
            vec3 albedo = mix(uPrimary, uAccent, pat);
            albedo *= 0.88 + scales * 0.22;

            // Противотеневая окраска: спина темнее, брюхо светлее
            float belly = smoothstep(-0.4, 0.5, -N.y);
            albedo = mix(albedo * 0.60, albedo + vec3(0.18), belly * 0.6);

            // Жаберные щели у акулы
            if (uForm == 5) {
                float gz = (vLocal.z - 0.55) * 7.0;
                float gill = smoothstep(0.75, 1.0, sin(gz)) * step(abs(vLocal.y), 0.35) * step(0.3, abs(vLocal.x));
                albedo *= 1.0 - gill * 0.55;
            }

            // ── Глаз: считаем в локальном пространстве, зеркаля по X ────────
            float eyeMask = 0.0, pupil = 0.0, glint = 0.0;
            if (uEye.w > 0.001) {
                vec3 lp = vec3(abs(vLocal.x), vLocal.y, vLocal.z);
                float d = distance(lp, uEye.xyz) / uEye.w;
                eyeMask = 1.0 - smoothstep(0.85, 1.0, d);
                pupil   = 1.0 - smoothstep(0.38, 0.52, d);
                glint   = 1.0 - smoothstep(0.10, 0.22, distance(lp, uEye.xyz + vec3(0.0, 0.32, 0.32) * uEye.w) / uEye.w);
            }

            float irid = fres * (sin(vBodyU * 20.0 + uTime * 0.8) * 0.5 + 0.5);
            float caustic = causticField(vWorldPos, uTime) * 0.85;

            vec3 ambient = uAmbientColor * albedo;
            vec3 color = ambient
                       + albedo * diff * uLightColor
                       + albedo * back * uLightColor
                       + uLightColor * spec * mix(0.55, 1.35, uMetallic)
                       + uGlow * fres * 1.15
                       + uGlow * irid * 0.32
                       + uLightColor * caustic * 0.55;

            // Глаз рисуется поверх окраски
            color = mix(color, vec3(0.90, 0.92, 0.95) * (0.35 + diff), eyeMask);
            color = mix(color, vec3(0.02, 0.02, 0.03), pupil);
            color += vec3(1.0) * glint * 0.9;

            color = mix(color, vec3(1.0, 0.30, 0.12), uPanic * 0.35);
            color = mix(color, vec3(0.35, 0.0, 0.02), uBloodTint * 0.7);

            color = applyFog(color, vViewDist);
            fragColor = vec4(tonemapACES(color), uOpacity);
        }
    """.trimIndent()

    // ═══════════════════════════ ДНО ═══════════════════════════
    val WORLD_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform vec3 uCameraPos;
        out vec3  vWorldPos;
        out vec3  vNormal;
        out vec2  vTexCoord;
        out float vViewDist;
        void main() {
            vec4 world = uModel * vec4(aPosition, 1.0);
            vWorldPos  = world.xyz;
            vNormal    = normalize(mat3(uModel) * aNormal);
            vTexCoord  = aTexCoord;
            vViewDist  = distance(uCameraPos, world.xyz);
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val FLOOR_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in vec2  vTexCoord;
        in float vViewDist;
        uniform float uTime;
        uniform vec3  uLightDir;
        uniform vec3  uSandColor;
        $COMMON_FS
        out vec4 fragColor;

        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float vnoise(vec2 p) {
            vec2 i = floor(p), f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1,0)), f.x),
                       mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
        }

        void main() {
            vec3 N = normalize(vNormal);
            vec3 L = normalize(uLightDir);

            // Песок трёх масштабов: дюны, зерно, микрокрупинки
            float grain = vnoise(vWorldPos.xz * 8.0) * 0.48
                        + vnoise(vWorldPos.xz * 30.0) * 0.34
                        + vnoise(vWorldPos.xz * 110.0) * 0.18;
            float ripple = sin(vWorldPos.x * 2.7 + vnoise(vWorldPos.xz * 0.45) * 6.0) * 0.5 + 0.5;

            vec3 sand = uSandColor * (0.58 + grain * 0.62);
            sand = mix(sand, sand * 1.24, ripple * 0.34);

            float diff = max(dot(N, L), 0.0) * 0.82 + 0.18;
            vec3 color = sand * diff * uLightColor + uAmbientColor * sand;

            color += uLightColor * causticField(vWorldPos, uTime) * 1.7;

            // Отдельные искрящиеся песчинки
            float sparkle = step(0.9875, vnoise(vWorldPos.xz * 175.0 + floor(uTime * 2.5)));
            color += uLightColor * sparkle * 0.30;

            color = applyFog(color, vViewDist);
            fragColor = vec4(tonemapACES(color), 1.0);
        }
    """.trimIndent()

    val ROCK_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in vec2  vTexCoord;
        in float vViewDist;
        uniform vec3  uBaseColor;
        uniform vec3  uMossColor;
        uniform vec3  uLightDir;
        uniform vec3  uCameraPos;
        uniform float uTime;
        $COMMON_FS
        out vec4 fragColor;

        float hash3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 45.164))) * 43758.5453); }
        float noise3(vec3 p) {
            vec3 i = floor(p), f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(mix(hash3(i), hash3(i + vec3(1,0,0)), f.x),
                           mix(hash3(i + vec3(0,1,0)), hash3(i + vec3(1,1,0)), f.x), f.y),
                       mix(mix(hash3(i + vec3(0,0,1)), hash3(i + vec3(1,0,1)), f.x),
                           mix(hash3(i + vec3(0,1,1)), hash3(i + vec3(1,1,1)), f.x), f.y), f.z);
        }

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uLightDir);
            vec3 H = normalize(L + V);

            float rough = noise3(vWorldPos * 2.4) * 0.6 + noise3(vWorldPos * 9.5) * 0.4;
            vec3 base = uBaseColor * (0.5 + rough * 0.8);

            // Обрастание оседает на верхних гранях и слабо пульсирует
            float up = smoothstep(0.15, 0.85, N.y);
            float mossMask = smoothstep(0.42, 0.72, noise3(vWorldPos * 1.6)) * up;
            float pulse = sin(uTime * 1.3 + vWorldPos.x * 0.7 + vWorldPos.z * 0.5) * 0.5 + 0.5;
            base = mix(base, uMossColor * (0.65 + pulse * 0.6), mossMask * 0.8);

            float diff = max(dot(N, L), 0.0);
            float spec = pow(max(dot(N, H), 0.0), 16.0) * 0.18;

            vec3 color = base * diff * uLightColor + uAmbientColor * base
                       + uLightColor * spec
                       + uMossColor * mossMask * pulse * 0.5
                       + uLightColor * causticField(vWorldPos, uTime) * 0.9 * up;

            color = applyFog(color, vViewDist);
            fragColor = vec4(tonemapACES(color), 1.0);
        }
    """.trimIndent()

    // ═══════════════════════════ РАСТЕНИЯ ═══════════════════════════
    val PLANT_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;

        uniform mat4  uMVP;
        uniform mat4  uModel;
        uniform vec3  uCameraPos;
        uniform float uTime;
        uniform float uPhase;
        uniform float uStiffness;
        uniform float uCurrent;      // сила донного течения из настроек

        out vec3  vWorldPos;
        out vec3  vNormal;
        out vec2  vTexCoord;
        out float vViewDist;
        out float vHeight;

        void main() {
            vec3 pos = aPosition;
            // Плечо изгиба растёт нелинейно: основание жёсткое, верхушка гуляет
            float h = clamp(pos.y, 0.0, 10.0);
            float lever = pow(h, 1.6) * (1.0 - uStiffness * 0.65) * uCurrent;
            float t = uTime * 0.95 + uPhase;
            pos.x += (sin(t + h * 0.55) * 0.062 + sin(t * 2.3 + h * 0.95) * 0.019) * lever;
            pos.z += (cos(t * 0.78 + h * 0.42) * 0.044) * lever;

            vec4 world = uModel * vec4(pos, 1.0);
            vWorldPos = world.xyz;
            vNormal   = normalize(mat3(uModel) * aNormal);
            vTexCoord = aTexCoord;
            vHeight   = aTexCoord.y;
            vViewDist = distance(uCameraPos, world.xyz);
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    val PLANT_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in vec2  vTexCoord;
        in float vViewDist;
        in float vHeight;
        uniform vec3  uColor;
        uniform vec3  uTipColor;
        uniform vec3  uLightDir;
        uniform vec3  uCameraPos;
        uniform float uTime;
        $COMMON_FS
        out vec4 fragColor;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            if (dot(N, V) < 0.0) N = -N;
            vec3 L = normalize(uLightDir);

            float diff = max(dot(N, L), 0.0);
            // Просвечивание листа насквозь — главный признак живого растения в воде
            float trans = pow(max(dot(-N, L), 0.0), 1.8) * 0.9;

            float vein = 1.0 - smoothstep(0.03, 0.15, abs(vTexCoord.x - 0.5));
            vec3 base = mix(uColor, uTipColor, smoothstep(0.35, 1.0, vHeight));
            base = mix(base, base * 1.3, vein * 0.45);

            float pulse = sin(uTime * 2.0 + vWorldPos.x * 1.2 + vWorldPos.z * 0.8) * 0.5 + 0.5;
            float tipGlow = smoothstep(0.68, 1.0, vHeight);

            vec3 color = base * (diff + trans) * uLightColor
                       + uAmbientColor * base
                       + uTipColor * tipGlow * (0.25 + pulse * 0.55)
                       + uLightColor * causticField(vWorldPos, uTime) * 0.5;

            color = applyFog(color, vViewDist);
            fragColor = vec4(tonemapACES(color), 1.0);
        }
    """.trimIndent()

    // ═══════════════════════════ КОРМ ═══════════════════════════
    val FOOD_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in vec2  vTexCoord;
        in float vViewDist;
        uniform vec3  uBaseColor;
        uniform vec3  uLightDir;
        uniform vec3  uCameraPos;
        uniform float uTime;
        uniform int   uIsMeat;
        $COMMON_FS
        out vec4 fragColor;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            if (dot(N, V) < 0.0) N = -N;
            vec3 L = normalize(uLightDir);
            vec3 H = normalize(L + V);

            vec3 col = uBaseColor;
            float gloss = 0.15;

            if (uIsMeat == 1) {
                // Мраморность: волокна мышц и прослойки жира
                float fiber = sin(vTexCoord.x * 40.0 + sin(vTexCoord.y * 22.0) * 2.0) * 0.5 + 0.5;
                float fat = smoothstep(0.72, 0.95, sin(vTexCoord.y * 15.0 + vTexCoord.x * 9.0) * 0.5 + 0.5);
                col = mix(uBaseColor, vec3(0.42, 0.05, 0.06), fiber * 0.55);
                col = mix(col, vec3(0.92, 0.88, 0.80), fat * 0.5);
                gloss = 0.75;              // сырое мясо влажно бликует
            } else {
                float flake = sin(vTexCoord.x * 60.0) * sin(vTexCoord.y * 60.0) * 0.5 + 0.5;
                col *= 0.75 + flake * 0.5;
            }

            float diff = max(dot(N, L), 0.0);
            float spec = pow(max(dot(N, H), 0.0), 42.0) * gloss;
            vec3 color = col * diff * uLightColor + uAmbientColor * col + uLightColor * spec;
            color = applyFog(color, vViewDist);
            fragColor = vec4(tonemapACES(color), 1.0);
        }
    """.trimIndent()

    // ═══════════════════════════ ПУЗЫРИ ═══════════════════════════
    val BUBBLE_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        uniform mat4  uMVP;
        uniform mat4  uModel;
        uniform vec3  uCameraPos;
        uniform float uTime;
        uniform float uWobble;
        out vec3  vWorldPos;
        out vec3  vNormal;
        out float vViewDist;
        void main() {
            // Поверхностное натяжение непрерывно колеблет оболочку пузыря
            vec3 pos = aPosition;
            float d = sin(uTime * 6.5 + uWobble + aPosition.y * 7.5) * 0.06;
            pos *= (1.0 + d);
            pos.y *= (1.0 - d * 0.85);
            vec4 world = uModel * vec4(pos, 1.0);
            vWorldPos = world.xyz;
            vNormal   = normalize(mat3(uModel) * aNormal);
            vViewDist = distance(uCameraPos, world.xyz);
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    val BUBBLE_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in float vViewDist;
        uniform vec3  uCameraPos;
        uniform vec3  uLightDir;
        uniform float uTime;
        $COMMON_FS
        out vec4 fragColor;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uLightDir);
            vec3 H = normalize(L + V);

            float ndv  = max(dot(N, V), 0.0);
            float fres = pow(1.0 - ndv, 2.7);
            float spec = pow(max(dot(N, H), 0.0), 140.0);

            // Интерференция в тонкой плёнке: толщина плавает по высоте и во времени
            float thick = 3.3 + sin(vWorldPos.y * 5.5 + uTime * 1.7) * 1.5;
            vec3 irid = 0.5 + 0.5 * cos(6.28318 * thick * (1.0 - ndv) + vec3(0.0, 2.09, 4.19));

            vec3 color = mix(vec3(0.32, 0.60, 0.80), irid, fres * 0.88);
            color += vec3(1.0) * spec * 1.5;
            color += uLightColor * fres * 0.35;

            float alpha = clamp(fres * 0.82 + spec * 1.2 + 0.05, 0.0, 0.93);
            fragColor = vec4(tonemapACES(color), alpha * exp(-uFogDensity * vViewDist * 0.8));
        }
    """.trimIndent()

    // ═══════════════════════ ПОВЕРХНОСТЬ ВОДЫ ═══════════════════════
    val SURFACE_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        uniform mat4  uMVP;
        uniform mat4  uModel;
        uniform vec3  uCameraPos;
        uniform float uTime;
        out vec3  vWorldPos;
        out vec3  vNormal;
        out float vViewDist;

        void main() {
            vec3 pos = aPosition;
            float w1 = sin(pos.x * 0.55 + uTime * 1.10) * 0.26;
            float w2 = sin(pos.z * 0.72 - uTime * 0.83) * 0.20;
            float w3 = sin((pos.x + pos.z) * 0.33 + uTime * 1.60) * 0.12;
            pos.y += w1 + w2 + w3;

            // Аналитическая нормаль поверхности — без неё нет блика на гребнях волн
            float dx = cos(pos.x * 0.55 + uTime * 1.10) * 0.143
                     + cos((pos.x + pos.z) * 0.33 + uTime * 1.60) * 0.040;
            float dz = cos(pos.z * 0.72 - uTime * 0.83) * 0.144
                     + cos((pos.x + pos.z) * 0.33 + uTime * 1.60) * 0.040;
            vec3 n = normalize(vec3(-dx, 1.0, -dz));

            vec4 world = uModel * vec4(pos, 1.0);
            vWorldPos = world.xyz;
            vNormal   = normalize(mat3(uModel) * n);
            vViewDist = distance(uCameraPos, world.xyz);
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    val SURFACE_FS = """
        #version 300 es
        precision highp float;
        in vec3  vWorldPos;
        in vec3  vNormal;
        in float vViewDist;
        uniform vec3  uCameraPos;
        uniform vec3  uLightDir;
        uniform float uTime;
        $COMMON_FS
        out vec4 fragColor;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            if (dot(N, V) < 0.0) N = -N;
            vec3 L = normalize(uLightDir);
            vec3 H = normalize(L + V);

            float ndv = max(dot(N, V), 0.0);
            // Смотрим на поверхность снизу: за критическим углом ~48.6° она зеркалит
            float tir = smoothstep(0.66, 0.32, ndv);
            float spec = pow(max(dot(N, H), 0.0), 200.0);

            vec3 sky = uLightColor * 0.55 + vec3(0.10, 0.30, 0.45);
            vec3 color = sky * (0.30 + ndv * 0.70);
            color = mix(color, uFogColor * 1.4, tir * 0.82);
            color += uLightColor * spec * 2.4;

            float alpha = 0.28 + tir * 0.44;
            fragColor = vec4(tonemapACES(color), alpha * exp(-uFogDensity * vViewDist * 0.85));
        }
    """.trimIndent()

    // ═══════════════════════ ФОН И ЛУЧИ ═══════════════════════
    val BG_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        out vec2 vUv;
        void main() { vUv = aTexCoord; gl_Position = vec4(aPosition.xy, 0.999999, 1.0); }
    """.trimIndent()

    val BG_FS = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform float uTime;
        uniform vec3  uDeepColor;
        uniform vec3  uShallowColor;
        out vec4 fragColor;
        void main() {
            float depth = pow(clamp(vUv.y, 0.0, 1.0), 1.35);
            vec3 col = mix(uDeepColor, uShallowColor, depth);

            // Планктонная взвесь в толще воды
            vec2 p = vUv * vec2(64.0, 36.0);
            float m = fract(sin(dot(floor(p), vec2(41.3, 289.1))) * 43758.5453);
            float motes = smoothstep(0.9955, 1.0, m) * (0.5 + 0.5 * sin(uTime * 1.8 + m * 30.0));
            col += vec3(0.32, 0.60, 0.78) * motes * 0.15;

            vec2 d = vUv - 0.5;
            col *= 1.0 - dot(d, d) * 0.82;
            fragColor = vec4(col, 1.0);
        }
    """.trimIndent()

    val GODRAY_FS = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform float uTime;
        uniform vec3  uRayColor;
        uniform float uIntensity;
        out vec4 fragColor;
        void main() {
            vec2 p = vUv - vec2(0.42, 1.28);
            float ang = atan(p.x, -p.y);
            float dist = length(p);

            float rays = pow(max(sin(ang * 16.0 + uTime * 0.28), 0.0), 9.0)
                       + pow(max(sin(ang * 27.0 - uTime * 0.19 + 1.7), 0.0), 13.0) * 0.72
                       + pow(max(sin(ang * 41.0 + uTime * 0.13 + 3.1), 0.0), 17.0) * 0.48;

            // Затухание Бугера — Ламберта по глубине
            float atten = exp(-dist * 1.55) * smoothstep(0.0, 0.45, vUv.y);
            float shimmer = 0.78 + 0.22 * sin(uTime * 1.5 + ang * 7.0);
            fragColor = vec4(uRayColor * rays * atten * shimmer * uIntensity, 1.0);
        }
    """.trimIndent()

    // ═══════════════════════ ЧАСТИЦЫ ═══════════════════════
    val PARTICLE_VS = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec4 aColor;
        layout(location = 2) in vec2 aSizeLife;
        uniform mat4  uMVP;
        uniform float uViewportH;
        out vec4  vColor;
        out float vLife;
        void main() {
            vec4 clip = uMVP * vec4(aPosition, 1.0);
            gl_Position = clip;
            gl_PointSize = clamp(aSizeLife.x * uViewportH / max(clip.w, 0.001), 2.0, 160.0);
            vColor = aColor;
            vLife = aSizeLife.y;
        }
    """.trimIndent()

    val PARTICLE_FS = """
        #version 300 es
        precision mediump float;
        in vec4  vColor;
        in float vLife;
        uniform int uSoftCore;    // 1 = светящееся ядро (кавитация), 0 = плотное облако (кровь, снег)
        out vec4 fragColor;
        void main() {
            vec2 d = gl_PointCoord - vec2(0.5);
            float r2 = dot(d, d);
            if (r2 > 0.25) discard;                       // круглая точка вместо квадрата
            float falloff = 1.0 - smoothstep(0.0, 0.25, r2);
            if (uSoftCore == 1) {
                float core = pow(falloff, 3.0);
                fragColor = vec4(vColor.rgb * (falloff + core * 1.7), falloff * vColor.a);
            } else {
                fragColor = vec4(vColor.rgb, pow(falloff, 1.6) * vColor.a);
            }
        }
    """.trimIndent()
}