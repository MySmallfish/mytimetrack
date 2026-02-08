import Foundation

struct GreenInvoiceClient {
    let apiKey: String
    let apiSecret: String
    let baseURL: URL
    private let session: URLSession = .shared

    static let sandboxBaseURL = URL(string: "https://sandbox.d.greeninvoice.co.il/api/v1")!

    init(
        apiKey: String,
        apiSecret: String,
        baseURL: URL = URL(string: "https://api.greeninvoice.co.il/api/v1")!
    ) {
        self.apiKey = apiKey
        self.apiSecret = apiSecret
        self.baseURL = baseURL
    }

    func createProformaInvoice(
        subject: String,
        itemDescription: String,
        quantity: Double,
        unitPrice: Double,
        vatType: VatType,
        clientId: String,
        date: Date
    ) async throws -> String? {
        let token = try await fetchToken()
        let documentType = 300
        let currency = "USD"
        let payload = GreenInvoiceDocumentRequest(
            description: subject,
            client: GreenInvoiceClientReference(id: clientId, accountingKey: nil),
            type: documentType,
            date: Self.dateFormatter.string(from: date),
            lang: "en",
            currency: currency,
            vatType: vatType.rawValue,
            income: [
                GreenInvoiceIncomeItem(
                    description: itemDescription,
                    quantity: quantity,
                    price: unitPrice,
                    currency: currency,
                    currencyRate: 1,
                    vatType: vatType.rawValue
                )
            ]
        )

        var request = URLRequest(url: baseURL.appendingPathComponent("documents"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GreenInvoiceError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw GreenInvoiceError.server(statusCode: httpResponse.statusCode, message: message)
        }
        if let decoded = try? JSONDecoder().decode(GreenInvoiceResponse.self, from: data) {
            return decoded.id ?? decoded.documentId ?? decoded.number
        }
        return nil
    }

    func fetchClientId(accountingKey: String) async throws -> String? {
        let trimmed = accountingKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let token = try await fetchToken()
        return try await fetchClientId(accountingKey: trimmed, token: token)
    }

    private func fetchToken() async throws -> String {
        let payload = GreenInvoiceAuthRequest(id: apiKey, secret: apiSecret)
        var request = URLRequest(url: baseURL.appendingPathComponent("account/token"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GreenInvoiceError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw GreenInvoiceError.server(statusCode: httpResponse.statusCode, message: message)
        }

        if let decoded = try? JSONDecoder().decode(GreenInvoiceAuthResponse.self, from: data),
           let token = decoded.tokenValue {
            return token
        }

        if let token = extractToken(from: data) {
            return token
        }

        throw GreenInvoiceError.missingToken
    }

    private func fetchClientId(accountingKey: String, token: String) async throws -> String? {
        let queryKeys = ["accountingKey", "search", "query", "q"]
        let postPayloads: [[String: Any]] = [
            ["search": accountingKey],
            ["query": accountingKey],
            ["q": accountingKey],
            ["accountingKey": accountingKey],
            ["accountingNumber": accountingKey],
            ["customerKey": accountingKey],
            ["externalId": accountingKey],
            ["filter": ["accountingKey": accountingKey]],
            ["client": ["accountingKey": accountingKey]]
        ]

        var lastError: Error?
        var hadResponse = false

        for payload in postPayloads {
            do {
                let result = try await fetchClientId(
                    path: "clients/search",
                    token: token,
                    method: "POST",
                    queryItems: nil,
                    payload: payload,
                    accountingKey: accountingKey
                )
                hadResponse = true
                if let id = result {
                    return id
                }
            } catch {
                if isMethodNotAllowed(error) {
                    continue
                }
                lastError = error
            }
        }

        for queryKey in queryKeys {
            do {
                let result = try await fetchClientId(
                    path: "clients/search",
                    token: token,
                    method: "GET",
                    queryItems: [URLQueryItem(name: queryKey, value: accountingKey)],
                    payload: nil,
                    accountingKey: accountingKey
                )
                hadResponse = true
                if let id = result {
                    return id
                }
            } catch {
                if isMethodNotAllowed(error) {
                    continue
                }
                lastError = error
            }
        }

        for queryKey in queryKeys {
            do {
                let result = try await fetchClientId(
                    path: "clients",
                    token: token,
                    method: "GET",
                    queryItems: [URLQueryItem(name: queryKey, value: accountingKey)],
                    payload: nil,
                    accountingKey: accountingKey
                )
                hadResponse = true
                if let id = result {
                    return id
                }
            } catch {
                if isMethodNotAllowed(error) {
                    continue
                }
                lastError = error
            }
        }

        if hadResponse {
            return nil
        }
        if let lastError {
            throw lastError
        }
        return nil
    }

    private func fetchClientId(
        path: String,
        token: String,
        method: String,
        queryItems: [URLQueryItem]?,
        payload: [String: Any]?,
        accountingKey: String
    ) async throws -> String? {
        var url = baseURL.appendingPathComponent(path)
        if let queryItems, !queryItems.isEmpty {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = queryItems
            if let composed = components?.url {
                url = composed
            }
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        if let payload {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GreenInvoiceError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8)
            throw GreenInvoiceError.server(statusCode: httpResponse.statusCode, message: message)
        }

        return extractClientId(from: data, accountingKey: accountingKey)
    }

    private func isMethodNotAllowed(_ error: Error) -> Bool {
        if let greenError = error as? GreenInvoiceError {
            if case let .server(statusCode, _) = greenError, statusCode == 405 {
                return true
            }
        }
        return false
    }

    private func extractClientId(from data: Data, accountingKey: String) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) else { return nil }
        let normalizedKey = accountingKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        if let dict = json as? [String: Any] {
            if let id = stringValue(from: dict, keys: ["id", "clientId", "clientID", "_id"]) {
                return id
            }
            if let client = dict["client"] as? [String: Any],
               let id = stringValue(from: client, keys: ["id", "clientId", "clientID", "_id"]) {
                return id
            }
            for key in ["items", "data", "clients", "results"] {
                if let array = dict[key] as? [[String: Any]],
                   let id = findClientId(in: array, accountingKey: normalizedKey) {
                    return id
                }
            }
            return nil
        }

        if let array = json as? [[String: Any]] {
            return findClientId(in: array, accountingKey: normalizedKey)
        }

        return nil
    }

    private func findClientId(in clients: [[String: Any]], accountingKey: String) -> String? {
        let matchKeys = [
            "accountingKey", "accountingCode", "accountingId", "accountingNumber",
            "customerKey", "clientKey", "externalId", "externalID", "key", "code"
        ]

        for client in clients {
            for key in matchKeys {
                if let value = stringValue(from: client, keys: [key])?.lowercased(),
                   value == accountingKey {
                    return stringValue(from: client, keys: ["id", "clientId", "clientID", "_id"])
                }
            }
        }

        if clients.count == 1 {
            return stringValue(from: clients[0], keys: ["id", "clientId", "clientID", "_id"])
        }

        return nil
    }

    private func stringValue(from dict: [String: Any], keys: [String]) -> String? {
        for key in keys {
            if let value = dict[key] {
                if let string = value as? String, !string.isEmpty {
                    return string
                }
                if let number = value as? NSNumber {
                    return number.stringValue
                }
            }
        }
        return nil
    }

    private func extractToken(from data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) else { return nil }
        if let dict = json as? [String: Any] {
            return findToken(in: dict)
        }
        return nil
    }

    private func findToken(in dict: [String: Any]) -> String? {
        for key in ["token", "access_token", "accessToken"] {
            if let value = dict[key] as? String, !value.isEmpty {
                return value
            }
        }
        if let nested = dict["data"] as? [String: Any] {
            return findToken(in: nested)
        }
        return nil
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

struct GreenInvoiceDocumentRequest: Encodable {
    let description: String
    let client: GreenInvoiceClientReference?
    let type: Int
    let date: String
    let lang: String
    let currency: String
    let vatType: Int
    let income: [GreenInvoiceIncomeItem]
}

struct GreenInvoiceClientReference: Encodable {
    let id: String?
    let accountingKey: String?
}

struct GreenInvoiceIncomeItem: Encodable {
    let description: String
    let quantity: Double
    let price: Double
    let currency: String
    let currencyRate: Double
    let vatType: Int
}

struct GreenInvoiceAuthRequest: Encodable {
    let id: String
    let secret: String
}

struct GreenInvoiceAuthResponse: Decodable {
    let token: String?
    let accessToken: String?
    let data: TokenContainer?

    var tokenValue: String? {
        token ?? accessToken ?? data?.token ?? data?.accessToken
    }

    struct TokenContainer: Decodable {
        let token: String?
        let accessToken: String?
    }
}

struct GreenInvoiceResponse: Decodable {
    let id: String?
    let documentId: String?
    let number: String?
}

enum GreenInvoiceError: LocalizedError {
    case invalidResponse
    case server(statusCode: Int, message: String?)
    case missingToken

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from GreenInvoice."
        case let .server(statusCode, message):
            if let message, !message.isEmpty {
                return "GreenInvoice error \(statusCode): \(message)"
            }
            return "GreenInvoice error \(statusCode)."
        case .missingToken:
            return "Could not read an authentication token from GreenInvoice."
        }
    }
}
