# Hybrid Encryption — AES + RSA (NIBSS Integration)

Stepping back from the code to understand the "why" behind the architecture is exactly what separates a junior coder from a senior enterprise architect.

Since you are dealing with highly sensitive banking data (BVNs, Account Numbers, suspected fraud reasons), security is the absolute highest priority.

---

## 1. Why is data encrypted?

When your Spring Boot application sends a message over the internet to NIBSS, that data passes through multiple routers, firewalls, and internet service providers. Without encryption, the data is sent as "plaintext." If a malicious actor intercepts that traffic (a "Man-in-the-Middle" attack), they could easily read the BVNs and potentially alter the payload to unfreeze a fraudster's account.

Encryption mathematically scrambles this plaintext into unreadable gibberish (ciphertext). Even if a hacker intercepts the payload, it is completely useless to them without the specific mathematical "key" required to unscramble it.

---

## 2. The two primary types of encryption

### Symmetric Encryption — the fast padlock

- **How it works:** One single key is used to both encrypt and decrypt the data. Think of it like a house key — whoever holds the key can lock and unlock the door. In the NIBSS project, this is **AES-256-GCM**.
- **Pros:** Incredibly fast and efficient for encrypting large amounts of data (like big JSON payloads).
- **Cons:** The "Key Distribution Problem" — how do you securely get that single key to NIBSS? If you send it over the internet and a hacker intercepts it, they can unlock all your messages.

### Asymmetric Encryption — the public mailbox

- **How it works:** A mathematically linked **key pair** — a Public Key and a Private Key. You give your Public Key to everyone (like an open mailbox slot), but keep your Private Key strictly hidden. Anyone can use your Public Key to lock a message, but *only* your Private Key can unlock it. In the NIBSS project, this is **RSA-OAEP-256**.
- **Pros:** Completely solves the key distribution problem — you share the public key, never a secret.
- **Cons:** Mathematically heavy and extremely slow. Generally incapable of encrypting large payloads like an entire JSON request.

---

## 3. Why hybrid encryption?

NIBSS mandates a **Hybrid Encryption** scheme because it combines the **speed of Symmetric Encryption** with the **security of Asymmetric Encryption** — eliminating the drawbacks of both.

Here is exactly what happens when a staff member clicks "Submit" to report a BVN:

1. **The disposable key** — The application generates a brand new, ephemeral (one-time use) **AES-256 session key** specifically for this single request.
2. **The fast lock** — That AES key encrypts the actual JSON payload (the BVN, reason, transaction ID).
3. **The secure vault** — The app then takes the **NIBSS RSA Public Key** and uses it to encrypt the AES key itself.
4. **The delivery** — The encrypted payload, the encrypted AES key, and an Initialization Vector (IV) are packaged into the `EncryptedEnvelope` format and sent to NIBSS.

When NIBSS receives it, they use their **RSA Private Key** to unlock the AES key, then use that AES key to instantly decrypt the payload.

---

## 4. Benefits and drawbacks

### Benefits

- **Zero-Trust Security** — The gold standard for financial APIs. Even if NIBSS's network is compromised, an attacker cannot read the payloads without the private key.
- **Perfect Forward Secrecy** — Because a brand new AES key is generated for *every single request*, if a hacker somehow cracks one AES key, they only get access to that single payload — not the entire history of requests.
- **Authentication Guarantee** — When NIBSS hits your inbound webhooks, they encrypt the AES key with *Ecobank's Public Key*. The fact that your application can successfully decrypt it using your Private Key is definitive proof the message is authentic.

### Drawbacks

- **Implementation Complexity** — Significantly harder to code than a standard REST API. You have to handle byte arrays, Initialization Vectors (IVs), Authentication Tags, and Base64 encoding correctly.
- **Computational Overhead** — The server performs complex mathematical calculations (generating keys, encrypting, encoding) for every single request, consuming slightly more CPU than sending plaintext.

---

## The mental model

```
Sender side:
  JSON payload  →  encrypted with AES key (fast)
  AES key       →  encrypted with RSA public key (secure)
  
Package sent:  [ encrypted payload ] + [ encrypted AES key ] + [ IV ]

Receiver side:
  encrypted AES key  →  decrypted with RSA private key
  encrypted payload  →  decrypted with AES key
```

By taking this approach, you aren't just building a functional integration — you are building a highly resilient, enterprise-grade security gateway.
