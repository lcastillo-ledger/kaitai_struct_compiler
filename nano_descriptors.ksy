meta:
  id: nano_trusted_desc
  title: Nano Trusted Descriptors
  endian: be
  license: Unlicensed

doc: |
  A generic binary format for trusted descriptors (an info trusted by the nano signed by a Ledger key)
  
seq:
  - id: type
    type: u1
    enum: desc_type
    doc: Type of the descriptor
  - id: version
    contents: 
      - 0x01
    doc: Version, currently fixed to 0x01
  - id: key
    type: u1
    enum: key_enum
    doc: | 
      Signing key identifier, unique to Ledger. 
      The corresponding certificate must be passed to the application before use.
  - id: challenge
    type: challenge
    doc: An optional challenge to prove freshness of the descriptor
  - id: body
    doc: Body of the descriptor, based on type
    type:
      switch-on: type
      cases:
        'desc_type::plugin': plugin_body
        'desc_type::nft': nft_body
        'desc_type::name': name_body
  - id: sig
    type: signature
    doc: Signature of the descriptor computed over other fields 

types:
        
  plugin_body:
    doc: A plugin descriptor
    seq:
      - id: len_name
        type: u1
        doc: Length of the name field
      - id: name
        size: len_name
        doc: ASCII encoded name of the plugin to use
      - id: address
        size: 20
        doc: Blockchain smartcontract address associated with this plugin
      - id: selector
        size: 4
        doc: function selector in the smartcontract associated with this plugin
      - id: chain_id
        size: 8
        doc: Blockchain id, as specified in XXX
        
  nft_body:
    doc: An NFT collection descriptor
    seq:
      - id: len_name
        type: u1
        doc: Length of the name field
      - id: name
        size: len_name
        doc: UTF-8 encoded name of the collection corresponding to the address
      - id: address
        size: 20
        doc: Blockchain smartcontract address associated with this collection
      - id: chain_id
        size: 8
        doc: Blockchain id, as specified in XXX
        
  name_body:
    doc: A trusted name descriptor
    seq:
      - id: len_name
        type: u1
        doc: Length of the name field
      - id: name
        size: len_name
        doc: UTF-8 encoded truted name associated with this address
      - id: coin_type
        size: 4
        doc: SLIP 44 coin type as in [https://github.com/ensdomains/address-encoder]
      - id: len_address
        type: u1
        doc: Length of the address field
      - id: address
        size: len_address
        doc: Address value for this trusted name

  challenge:
    doc: An optional challenge enabling proving freshness of the descriptor
    seq:
      - id: len_challenge
        type: u1
        doc: length of the challenge, when no challenge is present use length of 0x00
      - id: challenge
        size: len_challenge
        doc: challenge as an array of raw bytes

  signature:
    doc: a signature container
    seq:
      - id: len_sig
        type: u1
        doc: Signature length
      - id: sig
        size: len_sig
        doc: |
          DER encoded signature.
          Signature is computed over serialized fields [type , version , key , challenge , body].
          Signature key and algorithm is determined by the [key] field and corresponding certificate.
  
enums:

  desc_type:
    0x01: 
      id: plugin
      doc: a plugin descriptor, mapping a smartcontract address to a nano plugin
    0x02: 
      id: nft
      doc: an nft collection descriptor, mapping an NFT address to a collection name
    0x03: 
      id: name
      doc: a trusted name descriptor, mapping a blockchain address to a displayable name
    
  key_enum:
    0x01: 
      id: test
      doc: test key, do not use in prod
    0x02: 
      id: persov2
      doc: PersoV2 Signing Key 01
    0x03: 
      id: plugin_selector_key
      doc: AWS Plugin Selector Signing Key 01
