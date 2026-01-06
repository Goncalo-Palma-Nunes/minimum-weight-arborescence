import hmac
import hashlib
import time
import random
import string
import base64
import urllib.parse
import requests

CLIENT_ID = "UToYjOisZCKGv8K0HxV0QB2R"
CLIENT_SECRET = "wxEIAO8GfqvMtKoe6LuMrWYNa6KhNTbd8s4b7Cx3OF"
BASE_URL = "https://rest.pubmlst.org"  # Replace with actual API base URL
AUTHORIZATION_URL = "http://pubmlst.org/cgi-bin/bigsdb/bigsdb.pl"  # BIGSdb authorization page


TRIPLES = [
    ("pubmlst_saureus_seqdef",
    "Staphylococcus aureus/in/cgMLST.csv",
    "https://rest.pubmlst.org/db/pubmlst_saureus_seqdef/schemes/20/profiles_csv"
    ),
    ("pubmlst_saureus_seqdef",
    "Staphylococcus aureus/in/mlst.csv",
    "https://rest.pubmlst.org/db/pubmlst_saureus_seqdef/schemes/1/profiles_csv"
    ),
    ("pubmlst_sagalactiae_seqdef",
    "Streptococcus agalactiae/in/mlst.csv",
    "https://rest.pubmlst.org/db/pubmlst_sagalactiae_seqdef/schemes/1/profiles_csv"
    ),
    ("pubmlst_spneumoniae_seqdef",
    "Streptococcus pneumoniae/in/mlst.csv",
    "https://rest.pubmlst.org/db/pubmlst_spneumoniae_seqdef/schemes/1/profiles_csv"
    ),
    ("pubmlst_spneumoniae_seqdef",
    "Streptococcus pneumoniae/in/cgMLST.csv",
    "https://rest.pubmlst.org/db/pubmlst_spneumoniae_seqdef/schemes/2/profiles_csv"
    ),

]


def generate_nonce(length=32):
    """Generate a random nonce string."""
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))


def generate_signature(method, url, params, consumer_secret, token_secret=''):
    """Generate HMAC-SHA1 signature for OAuth request."""
    # Sort parameters
    sorted_params = sorted(params.items())
    
    # Create parameter string
    param_string = '&'.join([f"{urllib.parse.quote(str(k), safe='')}={urllib.parse.quote(str(v), safe='')}" 
                             for k, v in sorted_params])
    
    # Create signature base string
    base_string = '&'.join([
        method.upper(),
        urllib.parse.quote(url, safe=''),
        urllib.parse.quote(param_string, safe='')
    ])
    
    # Create signing key
    signing_key = f"{urllib.parse.quote(consumer_secret, safe='')}&{urllib.parse.quote(token_secret, safe='')}"
    
    # Generate signature
    signature = hmac.new(
        signing_key.encode('utf-8'),
        base_string.encode('utf-8'),
        hashlib.sha1
    ).digest()
    
    return base64.b64encode(signature).decode('utf-8')


def get_request_token(database):
    """
    Get OAuth request token from the API.
    
    Args:
        database: The database name to access
        
    Returns:
        dict: Contains oauth_token, oauth_token_secret, and oauth_callback_confirmed
    """
    url = f"{BASE_URL}/db/{database}/oauth/get_request_token"
    
    # OAuth parameters
    oauth_params = {
        'oauth_consumer_key': CLIENT_ID,
        'oauth_signature_method': 'HMAC-SHA1',
        'oauth_timestamp': str(int(time.time())),
        'oauth_nonce': generate_nonce(),
        'oauth_callback': 'oob',
        'oauth_version': '1.0'
    }
    
    # Generate signature
    oauth_params['oauth_signature'] = generate_signature(
        'GET',
        url,
        oauth_params,
        CLIENT_SECRET
    )
    
    # Make request
    response = requests.get(url, params=oauth_params)
    response.raise_for_status()
    
    return response.json()


def get_authorization_url(database, oauth_token):
    """
    Generate the authorization URL for the user to grant access.
    
    Args:
        database: The database name to access
        oauth_token: The request token obtained from get_request_token()
        
    Returns:
        str: The URL where the user should authorize the application
    """
    params = {
        'db': database,
        'page': 'authorizeClient',
        'oauth_token': oauth_token
    }
    
    query_string = urllib.parse.urlencode(params)
    return f"{AUTHORIZATION_URL}?{query_string}"


def get_access_token(database, oauth_token, oauth_token_secret, verifier):
    """
    Exchange request token and verifier for an access token.
    
    Args:
        database: The database name to access
        oauth_token: The request token
        oauth_token_secret: The request token secret
        verifier: The verification code provided by the user
        
    Returns:
        dict: Contains oauth_token (access token) and oauth_token_secret
    """
    url = f"{BASE_URL}/db/{database}/oauth/get_access_token"
    
    # OAuth parameters
    oauth_params = {
        'oauth_consumer_key': CLIENT_ID,
        'oauth_token': oauth_token,
        'oauth_signature_method': 'HMAC-SHA1',
        'oauth_timestamp': str(int(time.time())),
        'oauth_nonce': generate_nonce(),
        'oauth_verifier': verifier,
        'oauth_version': '1.0'
    }
    
    # Generate signature using BOTH consumer_secret AND token_secret
    oauth_params['oauth_signature'] = generate_signature(
        'GET',
        url,
        oauth_params,
        CLIENT_SECRET,
        oauth_token_secret  # Include token secret for signing
    )
    
    # Make request
    response = requests.get(url, params=oauth_params)
    response.raise_for_status()
    
    return response.json()


def get_session_token(database, access_token, access_token_secret):
    """
    Get a session token using the access token.
    
    Args:
        database: The database name to access
        access_token: The access token obtained from get_access_token()
        access_token_secret: The access token secret
        
    Returns:
        dict: Contains oauth_token (session token) and oauth_token_secret
              Session token is valid for 12 hours.
    """
    url = f"{BASE_URL}/db/{database}/oauth/get_session_token"
    
    # OAuth parameters
    oauth_params = {
        'oauth_consumer_key': CLIENT_ID,
        'oauth_token': access_token,
        'oauth_signature_method': 'HMAC-SHA1',
        'oauth_timestamp': str(int(time.time())),
        'oauth_nonce': generate_nonce(),
        'oauth_version': '1.0'
    }
    
    # Generate signature using consumer_secret AND access_token_secret
    oauth_params['oauth_signature'] = generate_signature(
        'GET',
        url,
        oauth_params,
        CLIENT_SECRET,
        access_token_secret
    )
    
    # Make request
    response = requests.get(url, params=oauth_params)
    response.raise_for_status()
    
    return response.json()


def access_protected_resource(url, session_token, session_token_secret, method='GET', additional_params=None):
    """
    Access a protected API resource using the session token.
    
    Args:
        url: The full URL of the protected resource
        session_token: The session token obtained from get_session_token()
        session_token_secret: The session token secret
        method: HTTP method (default: 'GET')
        additional_params: Optional dict of additional query parameters
        
    Returns:
        Response object from the API call
    """
    # Start with OAuth parameters
    oauth_params = {
        'oauth_consumer_key': CLIENT_ID,
        'oauth_token': session_token,
        'oauth_signature_method': 'HMAC-SHA1',
        'oauth_timestamp': str(int(time.time())),
        'oauth_nonce': generate_nonce(),
        'oauth_version': '1.0'
    }
    
    # Combine OAuth params with any additional params for signature
    all_params = oauth_params.copy()
    if additional_params:
        all_params.update(additional_params)
    
    # Generate signature using consumer_secret AND session_token_secret
    oauth_params['oauth_signature'] = generate_signature(
        method,
        url,
        all_params,
        CLIENT_SECRET,
        session_token_secret
    )
    
    # Combine all parameters for the request
    request_params = oauth_params.copy()
    if additional_params:
        request_params.update(additional_params)
    
    # Make request
    if method.upper() == 'GET':
        response = requests.get(url, params=request_params)
    elif method.upper() == 'POST':
        response = requests.post(url, params=request_params)
    else:
        raise ValueError(f"Unsupported HTTP method: {method}")
    
    response.raise_for_status()
    return response

def write_response_to_file(response, output_path):
    """Write the API response content to a specified file."""
    # Ensure the output directory exists, create if necessary
    import os
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with open(output_path, 'w') as f:
        f.write(response.text)


if __name__ == "__main__":
    # Example usage
    # database = "pubmlst_spneumoniae_seqdef"  # Replace with your database
    # output_response_path = "Neisseria_sp/in/baaaaah.csv"  # Example output path
    # output_response_path = "Streptococcus pneumoniae/in/cgMLST.csv"  # Corrected output path
    # scheme = "https://rest.pubmlst.org/db/pubmlst_spneumoniae_seqdef/schemes/2/profiles_csv"
    triple = TRIPLES[1]  # Select the desired triple
    database, output_response_path, scheme = triple


    try:
        # Step 1: Get request token
        print("Step 1: Obtaining request token...")
        token_data = get_request_token(database)
        oauth_token = token_data['oauth_token']
        oauth_token_secret = token_data['oauth_token_secret']
        
        print("✓ Request Token obtained successfully")
        print(f"  oauth_token: {oauth_token}")
        print(f"  oauth_token_secret: {oauth_token_secret}")
        print()
        
        # Step 2: Get authorization URL
        print("Step 2: User authorization required")
        auth_url = get_authorization_url(database, oauth_token)
        print(f"Please visit this URL to authorize the application:")
        print(f"\n  {auth_url}\n")
        print("After authorizing, you will receive a verifier code.")
        print("The verifier code is valid for 60 minutes.")
        print()
        
        # Step 3: Get verifier from user
        verifier = input("Enter the verifier code: ").strip()
        
        # Step 4: Exchange for access token
        print("\nStep 3: Exchanging request token for access token...")
        access_data = get_access_token(database, oauth_token, oauth_token_secret, verifier)
        access_token = access_data['oauth_token']
        access_token_secret = access_data['oauth_token_secret']
        
        print("✓ Access Token obtained successfully!")
        print(f"  oauth_token (access): {access_token}")
        print(f"  oauth_token_secret (access): {access_token_secret}")
        print("\n✓ Authentication complete! You can now use these credentials to make API calls.")
        print("  Note: The access token does not expire but can be revoked by the user or administrator.")
        
        # Step 5: Get session token (optional, for actual API use)
        print("\nStep 4: Obtaining session token...")
        session_data = get_session_token(database, access_token, access_token_secret)
        session_token = session_data['oauth_token']
        session_token_secret = session_data['oauth_token_secret']
        
        print("✓ Session Token obtained successfully!")
        print(f"  oauth_token (session): {session_token}")
        print(f"  oauth_token_secret (session): {session_token_secret}")
        print("  Note: Session token is valid for 12 hours.")
        
        # Step 6: Access a protected resource (example)
        print("\nStep 5: Accessing protected resource...")
        print("Example: Fetching database info")
        
        resource_url = f"{BASE_URL}/db/{database}"
        response = access_protected_resource(
            resource_url,
            session_token,
            session_token_secret
        )
        
        print("✓ Successfully accessed protected resource!")
        print(f"Response status: {response.status_code}")
        print(f"Response preview: {response.text[:200]}..." if len(response.text) > 200 else f"Response: {response.text}")

        # GET request to fetch scheme profiles in CSV format
        print("\nFetching scheme profiles in CSV format...")
        response = access_protected_resource(
            scheme,
            session_token,
            session_token_secret
        )
        write_response_to_file(response, output_response_path)
        print(f"✓ Scheme profiles saved to {output_response_path}")
        
    except requests.exceptions.RequestException as e:
        print(f"Error: {e}")