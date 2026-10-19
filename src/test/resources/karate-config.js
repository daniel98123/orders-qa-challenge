function fn() {
  // baseUrl lo inyecta el runner (puerto aleatorio de Spring) o se pasa por -Dkarate.baseUrl
  // para ejecutar contra un ambiente desplegado (p. ej. el contenedor Docker en el pipeline).
  var baseUrl = karate.properties['baseUrl'] || karate.properties['karate.baseUrl'] || 'http://localhost:8080';
  karate.log('karate.env =', karate.env, '| baseUrl =', baseUrl);
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  karate.configure('logPrettyRequest', true);
  karate.configure('logPrettyResponse', true);
  karate.configure('headers', { 'X-Correlation-Id': 'karate-' + java.util.UUID.randomUUID() });
  return { baseUrl: baseUrl };
}
