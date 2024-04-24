# Transaction

This project provides tools to validate International Bank Account Numbers (IBANs) =>
=>            directly within your application, without relying on external APIs.
It supports validation against standardized IBAN formats for various countries <==>
to ensure the correctness of bank account details in international transactions.

## Features

- Validate IBANs for correctness and country-specific formats.
- Support for multiple countries with detailed regex validation rules.
- Easy integration with existing systems.
- No dependency on external services.

## Requirements

- Java 8 or higher
- Maven (for dependency management and running the project)


## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/MaksimVerzbitski/Transaction.git
   
2. Navigate to the project directory:
   ```bash
   cd Transaction
  
3. Compile the project using Maven:
   ```bash
   mvn compile
  
## Usage


To validate an IBAN, run the `IbanValidator` class with the IBAN as an argument:

```java
java IbanValidator 'BE71096123456769'



## Validating an IBAN

IBANs are validated using predefined regex patterns that match the country-specific IBAN structure. Here are examples of patterns for some countries:

- Germany (DE): `^DE\d{20}$`
- United Kingdom (GB): `^GB\d{2}[A-Z]{4}\d{14}$`

For a complete list of patterns and their explanations, see `IBAN.txt` included in the project files.


## Contributing

Contributions are welcome! If you have improvements or bug fixes:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Make your changes and commit them (`git commit -am 'Add some feature'`).
4. Push to the branch (`git push origin feature-branch`).
5. Create a new Pull Request.



## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
