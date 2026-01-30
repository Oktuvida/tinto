import { Injectable } from '@angular/core';

/**
 * Base HTTP client for Tinto API
 *
 * Handles:
 * - API key authentication with request signatures
 * - Base URL configuration
 * - Error handling
 * - Request/response logging
 */
@Injectable({
  providedIn: 'root'
})
export class ApiClient {
  private readonly baseUrl: string;
  private apiKey: string | null = null;

  constructor() {
    // Get base URL from environment or default to localhost
    this.baseUrl = this.getBaseUrl();
  }

  /**
   * Set API key for authentication
   */
  setApiKey(apiKey: string): void {
    this.apiKey = apiKey;
    // Store in sessionStorage (not localStorage for security)
    sessionStorage.setItem('tinto_api_key', apiKey);
  }

  /**
   * Get stored API key
   */
  getApiKey(): string | null {
    if (!this.apiKey) {
      this.apiKey = sessionStorage.getItem('tinto_api_key');
    }
    return this.apiKey;
  }

  /**
   * Clear API key (logout)
   */
  clearApiKey(): void {
    this.apiKey = null;
    sessionStorage.removeItem('tinto_api_key');
  }

  /**
   * Create request signature for authentication
   */
  private async createSignature(
    method: string,
    path: string,
    timestamp: string,
    body: string = ''
  ): Promise<string> {
    const apiKey = this.getApiKey();
    if (!apiKey) {
      throw new Error('API key not set');
    }

    // Create signature: SHA-512(apiKey + method + path + timestamp + body)
    const signatureInput = `${apiKey}:${method}:${path}:${timestamp}:${body}`;
    const encoder = new TextEncoder();
    const data = encoder.encode(signatureInput);
    
    // Use Web Crypto API for SHA-512
    const hashBuffer = await crypto.subtle.digest('SHA-512', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    
    return hashHex;
  }

  /**
   * Make authenticated GET request
   */
  async get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  /**
   * Make authenticated POST request
   */
  async post<T>(path: string, body: any): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  /**
   * Make authenticated PUT request
   */
  async put<T>(path: string, body: any): Promise<T> {
    return this.request<T>('PUT', path, body);
  }

  /**
   * Make authenticated DELETE request
   */
  async delete<T>(path: string): Promise<T> {
    return this.request<T>('DELETE', path);
  }

  /**
   * Make authenticated HTTP request
   */
  private async request<T>(
    method: string,
    path: string,
    body?: any
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const timestamp = new Date().toISOString();
    const bodyString = body ? JSON.stringify(body) : '';

    // Create headers with authentication
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Tinto-Timestamp': timestamp
    };

    // Add API key and signature if available
    const apiKey = this.getApiKey();
    if (apiKey) {
      const signature = await this.createSignature(method, path, timestamp, bodyString);
      headers['X-Tinto-API-Key'] = apiKey;
      headers['X-Tinto-Signature'] = signature;
    }

    // Make request
    const requestOptions: RequestInit = {
      method,
      headers,
      body: body ? bodyString : undefined
    };

    try {
      const response = await fetch(url, requestOptions);

      // Handle error responses
      if (!response.ok) {
        const error = await response.json();
        throw new ApiError(
          error.message || 'Request failed',
          response.status,
          error
        );
      }

      // Parse and return response
      return await response.json() as T;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      
      // Network or other errors
      throw new ApiError(
        'Network error occurred',
        0,
        { originalError: error }
      );
    }
  }

  /**
   * Get base URL from environment
   */
  private getBaseUrl(): string {
    // In production, this would come from environment configuration
    // For now, default to localhost
    return 'http://localhost:8080/api';
  }
}

/**
 * Custom error class for API errors
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public details?: any
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
