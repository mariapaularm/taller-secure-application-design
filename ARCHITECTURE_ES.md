# Enterprise Architecture: Secure Spring Application

## Descripción General


El sistema implementado demuestra y explica en profundidad una arquitectura de despliegue segura en **Amazon Web Services (AWS)** con separación estricta de responsabilidades entre el servicio de entrega web y el backend transaccional.

La aplicación consta de un cliente asíncrono (HTML+JS) entregado de forma segura vía TLS por Apache, y consumido posteriormente por una API REST en Spring Boot, donde las contraseñas se almacenan mediante hashes de BCrypt.

---

## Arquitectura del Sistema

### Diagrama de Relación y Red (Client ⭢ Apache ⭢ Spring)

```text
┌──────────────────────────────────────────────────────────────┐
│                    Navegador del Cliente                     │
│         (Cliente asíncrono HTML+JS con Fetch API)            │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTPS Público (TLS 1.2/1.3)
                               │ Certificado Let's Encrypt
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     Servidor Web Apache                      │
│                      (Instancia EC2 1)                       │
│  • Puertos Abiertos: 80 (Redirección) y 443 (HTTPS)          │
│  • Entrega de contenido estático protegido (Integridad)      │
│  • Proxy Inverso hacia el backend para las rutas /api/*      │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTPS Interno (TLS)
                               │ Certificado PKCS12 (Keystore)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     Backend Spring Boot                      │
│                      (Instancia EC2 2)                       │
│  • Puerto Abierto: Solo 5000 (Tráfico TCP interno desde EC2 1│
│  • API REST Segura y controladores de inicio de sesión       │
│  • Lógica de Autenticación con Sessions en memoria           │
│  • Base de datos H2 embebida (Cifrado BCrypt)                │
└──────────────────────────────────────────────────────────────┘
```

---

## Descripción Detallada de Componentes

### 1. Cliente (Navegador)

**Rol:** Interfaz de usuario final para autenticación e interacción con API

**Características:**
- JavaScript asincrónico (API Fetch) para solicitudes no bloqueantes
- Formularios de login/registro
- Comunicación segura vía HTTPS/TLS
- Gestión de sesión vía cookies HTTP-only

---

### 2. Servidor Web Apache (Instancia EC2 1 - Frontend & Proxy Reverse)

**Rol:** Despliegue de cara pública y entrega segura del cliente asíncrono.

**Demostración de Estrategias de Despliegue Seguro en AWS:**
- **Sistema Operativo:** Amazon Linux 2023.
- **Grupos de Seguridad (AWS SG):** Expuesto solo a IPv4/IPv6 en los puertos 80 y 443 para el tráfico público HTTP(S).
- **Puerto 80:** Solo permite `RewriteRule` para redirigir forzosamente el HTTP a HTTPS, garantizando *Secure By Default*.
- **Puerto 443:** Cifrado integral mediante un certificado de Let's Encrypt (Certbot), asegurando la Confidencialidad y la Integridad de la descarga inicial del cliente HTML+JS hacia el navegador.
- **Protocolos Criptográficos Seguros:** TLSv1.2 y TLSv1.3 habilitados explícitamente (`SSLProtocol -all +TLSv1.2 +TLSv1.3`). Deshabilitación de suites débiles (`!MD5`, `!aNULL`).
- **Separación Lógica:** Como Proxy Reverse, Apache reenvía todo el tráfico destinado a `/api/*` hacia la segunda instancia (Spring Boot), aislando el backend de la superficie de ataque primaria (Internet).

---

### 3. Backend Spring Boot (Instancia EC2 2 - API REST)

**Rol:** Proveer de endpoints asíncronos y manejar la lógica de estado y registro transaccional de autenticación.

**Demostración de Estrategias de Despliegue Seguro en AWS:**
- **Sistema Operativo:** Amazon Linux 2023 con OpenJDK 17.
- **Grupos de Seguridad (AWS SG):** Expuesto EXCLUSIVAMENTE a la IP o al Security Group del EC2 #1 de Apache mediante el puerto interno 5000. Nadie en internet puede acceder a esta instancia, limitando el Movimiento Lateral en AWS.
- **Seguridad Intrínseca TLS:** Incluso el tráfico desde la Instancia 1 hacia la Instancia 2 está encriptado usando HTTPS (SSL Keystore de tipo PKCS12 configurado en los *application properties* del servidor embebido de Tomcat).
- **H2 en Memoria & Hashes de Contraseñas:** Se implementó almacenamiento de credenciales por medio de **BCrypt**: ninguna contraseña real transita de vuelta a los logs ni queda registrada en texto plano dentro de la memoria, previniendo accesos ilícitos.

**Puntos Finales:**
| Método | Punto Final | Autenticación Requerida | Propósito |
|--------|-------------|-------------------------|-----------|
| POST | `/api/register` | No | Registrar nuevo usuario |
| POST | `/api/login` | No | Autenticar usuario |
| GET | `/api/user` | **Sí** | Obtener información del usuario actual |
| GET | `/api/hello` | **Sí** | Ejemplo de API protegida |
| POST | `/api/logout` | **Sí** | Limpiar sesión |

---

### 4. Base de Datos (H2)

**Rol:** Almacenamiento de credenciales de usuario

**Configuración:**
- Tipo: H2 embebida (en memoria)
- URL: `jdbc:h2:mem:securedb`
- ORM: Spring Data JPA con Hibernate
- Auto-inicialización: DDL configurado a `update`

**Esquema:**
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL -- cifrada con BCrypt
);
```

---

## Arquitectura de Seguridad

### Encriptación End-to-End

1. **Cliente ↔ Apache:**
   - Protocolo: HTTPS/TLS 1.2+
   - Certificado: Let's Encrypt (dominio válido)
   - Suite de Cifrado: HIGH, sin aNULL/MD5

2. **Apache ↔ Spring Boot:**
   - Protocolo: HTTPS/TLS 1.2+
   - Certificado: Autofirmado (red interna)
   - Sin validación de certificado (confianza de red interna)
   - HTTPS forzado por configuración de proxy inverso

3. **Spring Boot ↔ Base de Datos:**
   - Conexión directa en memoria (H2)
   - Sin transmisión de red
   - No se requiere encriptación adicional

### Flujo de Autenticación

```
1. Registro de Usuario:
   Cliente → [POST /api/register]
            [usuario, contraseña en texto plano]
            → Apache (Proxy)
            → Spring Boot
            → BCryptPasswordEncoder.encode(contraseña)
            → Guardar en Base de Datos H2

2. Inicio de Sesión de Usuario:
   Cliente → [POST /api/login]
            [usuario, contraseña en texto plano vía HTTPS]
            → Apache (Proxy)
            → Spring Boot
            → AuthenticationManager.authenticate()
            → BCrypt.matches(texto_plano, hash_almacenado)
            → Crear Sesión (JSESSIONID)
            → Retornar Cookie de Sesión (HTTP-only)

3. Acceso a API Protegida:
   Cliente → [GET /api/hello]
            [Con Cookie de Sesión]
            → Apache
            → Spring Boot
            → Spring Security valida sesión
            → Otorgar/Negar acceso según autenticación
```

### Seguridad de Contraseña

- **Algoritmo de Cifrado:** BCrypt (recomendado por OWASP)
- **Costo (Fortaleza):** 10 (predeterminado, ~100ms por cifrado)
- **Almacenamiento:** Solo en base de datos H2, nunca en registros/tránsito
- **Transmisión:** Solo sobre HTTPS/TLS

---

## Arquitectura de Implementación (AWS EC2)

### Infraestructura

**Instancia 1: Servidor Web Apache**
- Tipo de Instancia: t3.micro o t3.small
- Sistema Operativo: Amazon Linux 2023
- Grupo de Seguridad:
  - Entrada: 80/TCP, 443/TCP (desde 0.0.0.0/0)
  - Salida: 443/TCP a instancia Spring
- IP Elástica: Sí (para DNS)
- Volúmenes: 20GB gp3 volumen raíz

**Instancia 2: Backend Spring Boot**
- Tipo de Instancia: t3.micro o t3.small
- Sistema Operativo: Amazon Linux 2023
- Grupo de Seguridad:
  - Entrada: 5000/TCP (solo desde Grupo de Seguridad Apache)
  - Salida: Sin restricciones (para actualizaciones del sistema)
- IP Elástica: No (comunicación interna solamente)
- Volúmenes: 20GB gp3 volumen raíz

### Configuración de Red

- VPC: VPC predeterminada o personalizada
- Subnets: Mismo o diferente (ambas con acceso a internet para parches)
- Tabla de Rutas: Puerta de Enlace de Internet para acceso saliente
- Grupos de Seguridad: Implementar principio de menor privilegio (Apache solo se comunica con Spring en 5000)

---

## Diagrama de Comunicación Segura

```
┌─────────────────────────────────────────────────────┐
│           Canal TLS/HTTPS 1                         │
│    Cliente ←——HTTPS——→ Servidor Apache             │
│ (Puerto 443)      (Puerto 443)                      │
│   Grupo de Seguridad EC2: 80, 443 abiertos         │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│           Canal TLS/HTTPS 2                         │
│   Apache ←——HTTPS——→ Backend Spring Boot           │
│ (Proxy)        (Puerto 5000)                        │
│   GS de Apache: puede alcanzar Spring en 5000       │
│   GS de Spring: 5000 abierto solo a GS de Apache   │
└─────────────────────────────────────────────────────┘

Garantía de Seguridad:
✓ Cliente-Apache: Público, certificado validado, Let's Encrypt
✓ Apache-Spring: Interno, autofirmado (red confiable)
✓ Todas las transmisiones encriptadas (sin texto plano en red)
✓ Contraseñas nunca transmitidas en texto plano (cifradas antes del almacenamiento)
✓ Base de datos aislada (en memoria, sin acceso externo)
```
---