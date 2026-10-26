import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { API_URL } from './api.config';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

/**
 * Probamos el INTERCEPTOR sin servidor: HttpTestingController "captura" las peticiones que salen
 * y nos deja responderlas a mano (controlador.expectOne(...).flush(respuesta)).
 * El AuthService es un objeto falso con lo justo para el test (vi.fn() = función espía de Vitest).
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let controlador: HttpTestingController;
  let authFalso: { token: ReturnType<typeof vi.fn>; refrescar: ReturnType<typeof vi.fn>; sesionCaducada: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authFalso = {
      token: vi.fn().mockReturnValue('token-viejo'),
      refrescar: vi.fn(),
      sesionCaducada: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    http = TestBed.inject(HttpClient);
    controlador = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controlador.verify()); // falla si quedó alguna petición sin responder

  it('añade Authorization: Bearer a las peticiones a NUESTRA API', () => {
    http.get(`${API_URL}/api/v1/cuentas`).subscribe();

    const peticion = controlador.expectOne(`${API_URL}/api/v1/cuentas`);
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer token-viejo');
    peticion.flush([]);
  });

  it('NUNCA manda el token a otras webs', () => {
    http.get('https://otra-web.example/datos').subscribe();

    const peticion = controlador.expectOne('https://otra-web.example/datos');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });

  it('el login no lleva token', () => {
    http.post(`${API_URL}/api/v1/auth/login`, {}).subscribe();

    const peticion = controlador.expectOne(`${API_URL}/api/v1/auth/login`);
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });

  it('si llega un 401, renueva el token y REPITE la petición (el usuario ni se entera)', () => {
    authFalso.refrescar.mockReturnValue(of('token-nuevo'));
    let respuesta: unknown;
    http.get(`${API_URL}/api/v1/cuentas`).subscribe((r) => (respuesta = r));

    controlador.expectOne(`${API_URL}/api/v1/cuentas`).flush(null, { status: 401, statusText: 'Unauthorized' });

    const repetida = controlador.expectOne(`${API_URL}/api/v1/cuentas`);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer token-nuevo');
    repetida.flush(['cuenta 1']);

    expect(respuesta).toEqual(['cuenta 1']);
    expect(authFalso.sesionCaducada).not.toHaveBeenCalled();
  });

  it('si tampoco se puede renovar, manda al login', () => {
    authFalso.refrescar.mockReturnValue(throwError(() => new Error('refresh caducado')));
    let error: unknown;
    http.get(`${API_URL}/api/v1/cuentas`).subscribe({ error: (e) => (error = e) });

    controlador.expectOne(`${API_URL}/api/v1/cuentas`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authFalso.sesionCaducada).toHaveBeenCalledTimes(1);
    expect(error).toBeTruthy();
  });

  it('un 403 NO intenta renovar el token (no es un problema de sesión)', () => {
    http.get(`${API_URL}/api/admin/usuarios`).subscribe({ error: () => {} });

    controlador.expectOne(`${API_URL}/api/admin/usuarios`).flush(null, { status: 403, statusText: 'Forbidden' });

    expect(authFalso.refrescar).not.toHaveBeenCalled();
  });
});
